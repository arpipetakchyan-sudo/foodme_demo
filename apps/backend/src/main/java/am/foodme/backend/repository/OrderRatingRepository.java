package am.foodme.backend.repository;

import am.foodme.backend.model.OrderRating;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRatingRepository extends JpaRepository<OrderRating, Long> {

    boolean existsByOrderId(Long orderId);

    /** Average stars across all ratings the chef has received, or null if none. */
    @Query("SELECT AVG(r.stars) FROM OrderRating r WHERE r.chef.id = :chefId")
    Double averageStarsForChef(@Param("chefId") Long chefId);
}
