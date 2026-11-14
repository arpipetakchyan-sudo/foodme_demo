package am.foodme.backend.service;

import am.foodme.backend.dto.OrderDto;
import am.foodme.backend.dto.OrderRatingRequestDto;
import am.foodme.backend.exceptionHandler.BadRequestException;
import am.foodme.backend.exceptionHandler.ConflictException;
import am.foodme.backend.exceptionHandler.NotFoundException;
import am.foodme.backend.model.Chef;
import am.foodme.backend.model.Customer;
import am.foodme.backend.model.Order;
import am.foodme.backend.model.OrderRating;
import am.foodme.backend.repository.ChefRepository;
import am.foodme.backend.repository.CustomerRepository;
import am.foodme.backend.repository.OrderRatingRepository;
import am.foodme.backend.repository.OrderRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/** SCRUM-4: customers rate their delivered orders; each rating refreshes the chef's average. */
@Service
public class OrderRatingService {

    static final String MSG_ORDER_NOT_FOUND = "Order not found";
    static final String MSG_NOT_DELIVERED = "Only delivered orders can be reviewed.";
    static final String MSG_ALREADY_REVIEWED = "Order already reviewed.";
    static final String MSG_BAD_STARS = "Stars must be a whole number from 1 to 5.";
    static final String MSG_COMMENT_TOO_LONG = "Comment must be at most 1000 characters.";
    static final int MAX_COMMENT_LENGTH = 1000;

    private final OrderRepository orderRepository;
    private final OrderRatingRepository orderRatingRepository;
    private final ChefRepository chefRepository;
    private final CustomerRepository customerRepository;
    private final MeterRegistry meterRegistry;

    public OrderRatingService(OrderRepository orderRepository, OrderRatingRepository orderRatingRepository,
                              ChefRepository chefRepository, CustomerRepository customerRepository,
                              MeterRegistry meterRegistry) {
        this.orderRepository = orderRepository;
        this.orderRatingRepository = orderRatingRepository;
        this.chefRepository = chefRepository;
        this.customerRepository = customerRepository;
        this.meterRegistry = meterRegistry;
    }

    @Transactional
    public OrderDto rate(String customerEmail, String orderNumber, OrderRatingRequestDto request) {
        Customer customer = customerRepository.findByEmail(customerEmail == null ? "" : customerEmail.trim().toLowerCase())
                .orElseThrow(() -> new NotFoundException(MSG_ORDER_NOT_FOUND));
        Order order = orderRepository.findByNumber(orderNumber)
                .orElseThrow(() -> new NotFoundException(MSG_ORDER_NOT_FOUND));
        // R7: someone else's order behaves exactly like a missing one.
        if (order.getCustomer() == null || !order.getCustomer().getId().equals(customer.getId())) {
            throw new NotFoundException(MSG_ORDER_NOT_FOUND);
        }
        if (!"DELIVERED".equals(order.getStatus())) {
            throw new BadRequestException(MSG_NOT_DELIVERED);
        }

        int stars = validateStars(request == null ? null : request.getStars());
        String comment = normalizeComment(request == null ? null : request.getComment());

        // Lock the chef row first: concurrent ratings for this chef (including a double submit of
        // this very order) queue up here, so the "already reviewed" check and the average below
        // always see every committed rating.
        Chef chef = chefRepository.findByIdForUpdate(order.getChef().getId())
                .orElseThrow(() -> new NotFoundException("Chef not found"));
        if (orderRatingRepository.existsByOrderId(order.getId())) {
            throw new ConflictException(MSG_ALREADY_REVIEWED);
        }

        OrderRating rating = new OrderRating();
        rating.setOrder(order);
        rating.setCustomer(customer);
        rating.setChef(chef);
        rating.setStars(stars);
        rating.setComment(comment);
        rating.setCreatedAt(LocalDateTime.now());
        try {
            orderRatingRepository.saveAndFlush(rating);
        } catch (DataIntegrityViolationException e) {
            // Backstop for the unique(order_id) constraint.
            throw new ConflictException(MSG_ALREADY_REVIEWED);
        }

        chef.setRating(roundToOneDecimal(orderRatingRepository.averageStarsForChef(chef.getId())));
        meterRegistry.counter("foodme.orders.rated", "stars", String.valueOf(stars)).increment();

        order.setRating(rating);
        return OrderDto.mapEntityToDto(order);
    }

    static int validateStars(Double stars) {
        if (stars == null || stars.isNaN() || stars != Math.rint(stars) || stars < 1 || stars > 5) {
            throw new BadRequestException(MSG_BAD_STARS);
        }
        return stars.intValue();
    }

    static String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        String trimmed = comment.trim();
        if (trimmed.length() > MAX_COMMENT_LENGTH) {
            throw new BadRequestException(MSG_COMMENT_TOO_LONG);
        }
        return trimmed;
    }

    static Double roundToOneDecimal(Double value) {
        return value == null ? null : BigDecimal.valueOf(value).setScale(1, RoundingMode.HALF_UP).doubleValue();
    }
}
