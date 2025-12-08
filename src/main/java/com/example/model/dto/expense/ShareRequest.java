package com.example.model.dto.expense;

import io.micronaut.serde.annotation.Serdeable;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Serdeable
@Data
public class ShareRequest {

    @NotNull
    private Long userId;
    // Only used for EXACT split
    @DecimalMin(value = "0.00")
    private BigDecimal amount;

    // Only used for PERCENT split
    @Min(0)
    @Max(100)
    private Integer percent;

    public ShareRequest(long l, BigDecimal bigDecimal, int i) {
    }
}
