package com.pipedevliv.ticket.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TicketStatsDTO {
    private long totalTickets;
    private Map<String, Long> countByStatus;
    private Map<String, Long> countByPriority;
}
