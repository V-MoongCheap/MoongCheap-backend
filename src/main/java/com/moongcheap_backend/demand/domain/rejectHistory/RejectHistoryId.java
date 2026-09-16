package com.moongcheap_backend.demand.domain.rejectHistory;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class RejectHistoryId implements Serializable {

    private Long demandId;
    private Long demandBoardId;
}
