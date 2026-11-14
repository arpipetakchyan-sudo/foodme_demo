package am.foodme.backend.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

/** A customer's 1-5 star rating of one delivered order (SCRUM-4). At most one per order. */
@Entity
@Table(name = "order_rating", schema = "foodme")
@Getter
@Setter
public class OrderRating {

    @Id
    @SequenceGenerator(name = "order_rating_id_seq", sequenceName = "foodme.order_rating_id_seq", allocationSize = 1)
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "order_rating_id_seq")
    @Column(name = "id", nullable = false)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "chef_id", nullable = false)
    private Chef chef;

    @Column(name = "stars", nullable = false)
    private Integer stars;

    @Column(name = "comment", length = 1000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
