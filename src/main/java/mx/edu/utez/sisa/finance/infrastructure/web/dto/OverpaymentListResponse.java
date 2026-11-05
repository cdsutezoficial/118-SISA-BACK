package mx.edu.utez.sisa.finance.infrastructure.web.dto;

import java.util.List;

public record OverpaymentListResponse(List<OverpaymentResponse> items, int total) {
}
