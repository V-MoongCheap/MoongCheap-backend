function defaultBackendUrl() {
  return ["localhost", "127.0.0.1"].includes(window.location.hostname)
    ? "http://localhost:8080"
    : "https://api.moongcheap.shop";
}

const state = {
  backendUrl: localStorage.getItem("brandpay.backendUrl") || defaultBackendUrl(),
  brandpay: null,
  customerKey: null,
  authenticated: false,
};

const $ = (id) => document.getElementById(id);
const backendInput = $("backendUrl");
const connectButton = $("connectButton");
const addMethodButton = $("addMethodButton");
const syncButton = $("syncButton");
const refreshButton = $("refreshButton");
const devLoginButton = $("devLogin");
const newCustomerButton = $("newCustomerButton");
const createOrderButton = $("createOrderButton");
const executePaymentButton = $("executePaymentButton");
const statusBox = document.querySelector(".status-box");

backendInput.value = state.backendUrl;
function brandPayRedirectUrl() {
  return isLocalBackend()
    ? `${state.backendUrl}/api/dev/brandpay-test/callback`
    : `${state.backendUrl}/api/payments/brandpay/callback`;
}

$("redirectUrl").textContent = brandPayRedirectUrl();

function log(message, detail) {
  const time = new Date().toLocaleTimeString("ko-KR", { hour12: false });
  const suffix = detail ? `\n${JSON.stringify(detail, null, 2)}` : "";
  $("logOutput").textContent += `\n[${time}] ${message}${suffix}`;
  $("logOutput").scrollTop = $("logOutput").scrollHeight;
}

function mask(value) {
  if (!value || value.length < 12) return value || "—";
  return `${value.slice(0, 8)}••••${value.slice(-4)}`;
}

function setStatus(type, title, message) {
  statusBox.className = `status-box ${type || ""}`.trim();
  $("statusTitle").textContent = title;
  $("statusMessage").textContent = message;
}

function setSessionBadge(type, text) {
  $("sessionBadge").className = `badge ${type}`;
  $("sessionBadge").textContent = text;
}

function isLocalBackend() {
  try {
    return ["localhost", "127.0.0.1"].includes(new URL(state.backendUrl).hostname);
  } catch (_) {
    return false;
  }
}

function refreshVisibility() {
  document.querySelectorAll(".authenticated-only, .dev-only").forEach((element) => {
    const hiddenByAuthentication = element.classList.contains("authenticated-only")
      && !state.authenticated;
    const hiddenByEnvironment = element.classList.contains("dev-only")
      && !isLocalBackend();
    element.classList.toggle("hidden", hiddenByAuthentication || hiddenByEnvironment);
  });
  $("loginActions").classList.toggle("hidden", state.authenticated);
}

function resetAuthenticatedState() {
  state.authenticated = false;
  state.brandpay = null;
  state.customerKey = null;
  $("accountName").textContent = "—";
  $("clientKey").textContent = "—";
  $("customerKey").textContent = "—";
  $("methodList").replaceChildren();
  $("emptyMethods").classList.remove("hidden");
  addMethodButton.disabled = true;
  syncButton.disabled = true;
  refreshButton.disabled = true;
  executePaymentButton.disabled = true;
  createOrderButton.disabled = true;
  refreshVisibility();
}

async function api(path, options = {}) {
  const response = await fetch(`${state.backendUrl}${path}`, {
    credentials: "include",
    headers: { "Content-Type": "application/json", ...(options.headers || {}) },
    ...options,
  });

  if (!response.ok) {
    if (response.status === 401) {
      resetAuthenticatedState();
    }
    let body = null;
    try { body = await response.json(); } catch (_) { /* 본문이 없는 오류 */ }
    const error = new Error(body?.error?.message || body?.message || `HTTP ${response.status}`);
    error.status = response.status;
    error.code = body?.error?.code || body?.code;
    throw error;
  }

  return response.status === 204 ? null : response.json();
}

function updateLoginLinks() {
  $("kakaoLogin").href = `${state.backendUrl}/oauth2/authorization/kakao`;
  $("googleLogin").href = `${state.backendUrl}/oauth2/authorization/google`;
}

async function connect() {
  state.backendUrl = backendInput.value.trim().replace(/\/$/, "");
  localStorage.setItem("brandpay.backendUrl", state.backendUrl);
  updateLoginLinks();
  $("redirectUrl").textContent = brandPayRedirectUrl();
  setStatus("", "연결 중", "로그인 세션과 BrandPay 설정을 확인하고 있습니다.");
  connectButton.disabled = true;
  resetAuthenticatedState();

  try {
    const profile = await api("/api/members/me");
    const config = await api("/api/payments/brandpay/customer-key");
    if (!config.clientKey || !config.customerKey) {
      throw new Error("서버에서 clientKey 또는 customerKey를 받지 못했습니다.");
    }
    if (typeof window.TossPayments !== "function") {
      throw new Error("토스페이먼츠 SDK를 불러오지 못했습니다.");
    }

    const tossPayments = window.TossPayments(config.clientKey);
    state.brandpay = tossPayments.brandpay({
      customerKey: config.customerKey,
      redirectUrl: brandPayRedirectUrl(),
    });
    state.customerKey = config.customerKey;
    state.authenticated = true;

    $("accountName").textContent = profile.nickname || profile.loginId || "로그인 회원";
    $("clientKey").textContent = mask(config.clientKey);
    $("customerKey").textContent = mask(config.customerKey);
    refreshVisibility();
    addMethodButton.disabled = false;
    syncButton.disabled = false;
    refreshButton.disabled = false;
    executePaymentButton.disabled = false;
    createOrderButton.disabled = false;
    setSessionBadge("success", `${$("accountName").textContent} 로그인됨`);
    setStatus("success", "등록 준비 완료",
      "표시된 계정이 맞는지 확인한 뒤 결제수단을 추가하세요.");
    log("BrandPay SDK 초기화 완료", {
      clientKey: mask(config.clientKey), customerKey: mask(config.customerKey)
    });
    await loadMethods();
  } catch (error) {
    addMethodButton.disabled = true;
    syncButton.disabled = true;
    refreshButton.disabled = true;
    executePaymentButton.disabled = true;
    createOrderButton.disabled = true;
    if (error.status === 401) {
      setSessionBadge("error", "로그인 필요");
      setStatus("error", "로그인이 필요합니다",
        "아래에서 로그인한 뒤 이 화면으로 돌아와 세션 확인을 눌러주세요.");
    } else {
      setSessionBadge("error", "연결 실패");
      setStatus("error", "연결하지 못했습니다", error.message);
    }
    log("연결 실패", { status: error.status, code: error.code, message: error.message });
  } finally {
    refreshVisibility();
    connectButton.disabled = false;
  }
}

async function synchronize() {
  await api("/api/payments/brandpay/methods/synchronize", { method: "POST" });
  log("토스 결제수단을 서버 DB에 동기화했습니다.");
  await loadMethods();
}

async function addPaymentMethod() {
  if (!state.brandpay) return;
  addMethodButton.disabled = true;
  setStatus("", "토스 창 진행 중", "카드 또는 계좌 등록을 완료하세요.");
  try {
    await state.brandpay.addPaymentMethod();
    // 이미 인증된 고객은 redirect 없이 Promise가 끝날 수 있으므로 명시적으로 동기화한다.
    await synchronize();
    setStatus("success", "등록 완료", "토스와 서버 DB의 결제수단을 동기화했습니다.");
  } catch (error) {
    if (error.code === "USER_CANCEL") {
      setStatus("", "등록 취소", "사용자가 결제수단 등록 창을 닫았습니다.");
    } else {
      setStatus("error", "등록 실패", error.message || "SDK 오류가 발생했습니다.");
    }
    log("SDK 결제수단 등록 종료", { code: error.code, message: error.message });
  } finally {
    addMethodButton.disabled = false;
  }
}

async function loadMethods() {
  try {
    const methods = await api("/api/payments/methods");
    const list = $("methodList");
    list.replaceChildren();
    $("emptyMethods").classList.toggle("hidden", methods.length > 0);

    for (const method of methods) {
      const item = document.createElement("article");
      item.className = "method";
      const top = document.createElement("div");
      top.className = "method-top";
      const provider = document.createElement("strong");
      provider.textContent = method.provider;
      const status = document.createElement("span");
      status.className = "pill";
      status.textContent = method.isDefault ? "기본 · ACTIVE" : method.status;
      const number = document.createElement("p");
      number.textContent = `•••• ${method.number || "번호 없음"}`;
      const selector = document.createElement("input");
      selector.type = "radio";
      selector.name = "paymentMethodId";
      selector.value = method.id;
      selector.checked = method.isDefault || !document.querySelector(
        'input[name="paymentMethodId"]:checked');
      selector.setAttribute("aria-label", `${method.provider} 결제수단 선택`);
      top.append(selector, provider, status);
      item.append(top, number);
      list.append(item);
    }
    log(`서버 결제수단 ${methods.length}건 조회`);
  } catch (error) {
    setStatus("error", "목록 조회 실패", error.message);
    log("결제수단 목록 조회 실패", { code: error.code, message: error.message });
  }
}

async function createPendingOrder() {
  const selected = document.querySelector('input[name="paymentMethodId"]:checked');
  const amount = Number.parseInt($("paymentAmount").value, 10);
  const orderName = $("orderName").value.trim();
  if (!selected) {
    setStatus("error", "결제수단 선택", "먼저 등록된 결제수단을 선택하세요.");
    return;
  }
  if (!Number.isSafeInteger(amount) || amount < 100 || amount > 1_000_000
      || !orderName) {
    setStatus("error", "주문 정보 확인", "주문명과 100원 이상 100만원 이하 금액을 확인하세요.");
    return;
  }

  createOrderButton.disabled = true;
  setStatus("", "대기 주문 생성 중", "자동결제 전제조건을 갖춘 테스트 주문을 만들고 있습니다.");
  try {
    const result = await api("/api/dev/brandpay-test/orders", {
      method: "POST",
      body: JSON.stringify({
        paymentMethodId: Number(selected.value), amount, orderName
      }),
    });
    $("orderId").value = result.orderId;
    setStatus("success", "대기 주문 생성 완료",
      `주문 ${result.orderId} (${result.orderNo})을 결제할 수 있습니다.`);
    log("PAYMENT_PENDING 테스트 주문 생성", result);
  } catch (error) {
    setStatus("error", "대기 주문 생성 실패", error.message);
    log("대기 주문 생성 실패", {
      status: error.status, code: error.code, message: error.message
    });
  } finally {
    createOrderButton.disabled = false;
  }
}

async function executePayment() {
  const orderId = Number.parseInt($("orderId").value, 10);
  if (!Number.isSafeInteger(orderId) || orderId <= 0) {
    setStatus("error", "주문 ID 확인", "1 이상의 유효한 주문 ID를 입력하세요.");
    return;
  }

  executePaymentButton.disabled = true;
  $("paymentResult").classList.add("hidden");
  setStatus("", "자동결제 실행 중", `주문 ${orderId}의 결제를 처리하고 있습니다.`);
  try {
    const result = await api(`/api/dev/brandpay-test/payments/${orderId}/execute`, {
      method: "POST",
    });
    $("resultPaymentId").textContent = result.paymentId;
    $("resultOrderId").textContent = result.orderId;
    $("resultAmount").textContent = `${Number(result.amount).toLocaleString("ko-KR")}원`;
    $("resultStatus").textContent = result.status;
    $("resultAttempts").textContent = result.attemptCount;
    $("paymentResult").classList.remove("hidden");

    const succeeded = result.status === "SUCCEEDED";
    setStatus(succeeded ? "success" : "error",
      succeeded ? "결제 승인 완료" : "결제 상태 확인 필요",
      `Payment ${result.paymentId}: ${result.status}`);
    log("자동결제 실행 결과", result);
  } catch (error) {
    setStatus("error", "자동결제 실행 실패", error.message);
    log("자동결제 실행 실패", {
      status: error.status, code: error.code, message: error.message
    });
  } finally {
    executePaymentButton.disabled = false;
  }
}

connectButton.addEventListener("click", connect);
addMethodButton.addEventListener("click", addPaymentMethod);
syncButton.addEventListener("click", async () => {
  syncButton.disabled = true;
  try {
    await synchronize();
    setStatus("success", "동기화 완료", "서버의 결제수단 목록을 갱신했습니다.");
  } catch (error) {
    setStatus("error", "동기화 실패", error.message);
    log("수동 동기화 실패", { code: error.code, message: error.message });
  } finally {
    syncButton.disabled = false;
  }
});
refreshButton.addEventListener("click", loadMethods);
createOrderButton.addEventListener("click", createPendingOrder);
executePaymentButton.addEventListener("click", executePayment);
devLoginButton.addEventListener("click", async () => {
  devLoginButton.disabled = true;
  setStatus("", "테스트 로그인 중", "로컬 테스트 회원과 세션을 준비하고 있습니다.");
  try {
    const principal = await api("/api/dev/brandpay-test/login", { method: "POST" });
    log("로컬 테스트 세션 발급 완료", { memberId: principal.memberId });
    await connect();
  } catch (error) {
    setStatus("error", "테스트 로그인 실패", error.message);
    log("로컬 테스트 세션 발급 실패", { code: error.code, message: error.message });
  } finally {
    devLoginButton.disabled = false;
  }
});
newCustomerButton.addEventListener("click", async () => {
  newCustomerButton.disabled = true;
  setStatus("", "새 고객 생성 중", "새 customerKey를 사용할 테스트 세션을 준비합니다.");
  try {
    const principal = await api("/api/dev/brandpay-test/fresh-login", { method: "POST" });
    log("새 로컬 테스트 고객 세션 발급 완료", { memberId: principal.memberId });
    await connect();
  } catch (error) {
    setStatus("error", "새 고객 생성 실패", error.message);
    log("새 테스트 고객 생성 실패", { code: error.code, message: error.message });
  } finally {
    newCustomerButton.disabled = false;
  }
});

updateLoginLinks();
connect();
