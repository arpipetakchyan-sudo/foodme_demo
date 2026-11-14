package am.foodme.backend.repository;

import am.foodme.backend.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByNumber(String number);

    Page<Order> findByStatus(String status, Pageable pageable);

    Page<Order> findByCustomerIdOrderByCreatedAtDesc(Long customerId, Pageable pageable);

    @Query(value = "SELECT nextval('foodme.order_number_seq')", nativeQuery = true)
    long nextOrderNumberSequenceValue();

    /**
     * Atomically moves an order from {@code from} to {@code to}. Returns 1 if it moved, 0 if the
     * order was no longer in {@code from} — e.g. the kitchen accepted it a moment before the
     * customer cancelled. This is what keeps simultaneous cancel/accept from both succeeding.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "UPDATE foodme.\"order\" SET status = :to WHERE id = :id AND status = :from", nativeQuery = true)
    int transitionStatus(@Param("id") Long id, @Param("from") String from, @Param("to") String to);
}
