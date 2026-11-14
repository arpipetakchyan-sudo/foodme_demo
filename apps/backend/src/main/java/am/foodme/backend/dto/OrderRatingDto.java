package am.foodme.backend.dto;

import am.foodme.backend.model.OrderRating;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

/** A rating as shown on an order (My Orders, tracking, admin). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OrderRatingDto {
    private Integer stars;
    private String comment;
    private LocalDateTime createdAt;

    public static OrderRatingDto mapEntityToDto(OrderRating entity) {
        if (entity == null) {
            return null;
        }
        return new OrderRatingDto(entity.getStars(), entity.getComment(), entity.getCreatedAt());
    }
}
