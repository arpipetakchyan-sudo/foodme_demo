package am.foodme.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Body of POST /api/customer/orders/{number}/rating.
 * {@code stars} is a Double on purpose: with an Integer, Jackson would silently truncate 4.5 to 4,
 * but the spec says fractional stars must be rejected.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRatingRequestDto {
    private Double stars;
    private String comment;
}
