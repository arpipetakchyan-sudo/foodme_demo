package am.foodme.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-4: customers rate delivered orders. Test names reference the ticket's requirement (R*) and
 * acceptance-criteria (AC*) IDs.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Own database so ratings and orders created here can't affect other test classes
// (OrderControllerTest expects specific order numbers; ChefControllerTest reads seeded ratings).
@TestPropertySource(properties =
        "spring.datasource.url=jdbc:h2:mem:foodme_ratings;DB_CLOSE_DELAY=-1;MODE=PostgreSQL")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderRatingTest {

    /** Chef 1 is shared by most tests; chef 2 is used only by the average test so its maths is exact. */
    private static final long SHARED_CHEF_ID = 1;
    private static final long SHARED_DISH_ID = 1;
    private static final long AVERAGE_CHEF_ID = 2;
    private static final long AVERAGE_DISH_ID = 3;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --- helpers ------------------------------------------------------------------------------

    private String customerToken() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Ann",
                                "email", "rating-" + UUID.randomUUID() + "@example.com",
                                "phoneNumber", "+37491234567",
                                "password", "secret123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String adminToken() throws Exception {
        String response = mockMvc.perform(post("/admin/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("username", "admin", "password", "admin123"))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("token").asText();
    }

    private String placeOrder(String customerToken, long chefId, long dishId) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "chefId", chefId,
                "receiverName", "Ann",
                "receiverPhoneNumber", "+37491234567",
                "receiverEmail", "ann@example.com",
                "paymentType", "CASH",
                "deliveryMethod", "TAKEAWAY",
                "createOrderDishes", List.of(Map.of("dishId", dishId, "quantity", 1))));
        String response = mockMvc.perform(post("/api/order")
                        .header("Authorization", "Bearer " + customerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("number").asText();
    }

    private long orderId(String number) throws Exception {
        String response = mockMvc.perform(get("/api/order/number/" + number))
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void adminSetStatus(String adminToken, String number, String newStatus) throws Exception {
        mockMvc.perform(patch("/admin/order/" + orderId(number) + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", newStatus))))
                .andExpect(status().isOk());
    }

    /** Places an order for the given chef and walks it to DELIVERED. */
    private String deliveredOrder(String customerToken, long chefId, long dishId) throws Exception {
        String number = placeOrder(customerToken, chefId, dishId);
        String admin = adminToken();
        adminSetStatus(admin, number, "ACCEPTED");
        adminSetStatus(admin, number, "DELIVERED");
        return number;
    }

    private String deliveredOrder(String customerToken) throws Exception {
        return deliveredOrder(customerToken, SHARED_CHEF_ID, SHARED_DISH_ID);
    }

    private ResultActions rate(String token, String number, Object stars, String comment) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("stars", stars);
        if (comment != null) {
            body.put("comment", comment);
        }
        var request = post("/api/customer/orders/" + number + "/rating")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    // --- AC1: happy path, persisted, visible wherever the order is shown ----------------------

    @Test
    void ac1_customerRatesDeliveredOrder_andSeesItEverywhere() throws Exception {
        String token = customerToken();
        String number = deliveredOrder(token);

        rate(token, number, 5, "  Lovely, still warm!  ")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(number))
                .andExpect(jsonPath("$.rating.stars").value(5))
                .andExpect(jsonPath("$.rating.comment").value("Lovely, still warm!"))
                .andExpect(jsonPath("$.rating.createdAt").exists());

        // R13/R19: My Orders and tracking both show it on later reads.
        mockMvc.perform(get("/api/customer/orders").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.list[0].rating.stars").value(5));
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.rating.stars").value(5))
                .andExpect(jsonPath("$.rating.comment").value("Lovely, still warm!"));

        // AC6/R20: admin order details include stars, comment and date.
        mockMvc.perform(get("/admin/order/" + orderId(number)).header("Authorization", "Bearer " + adminToken()))
                .andExpect(jsonPath("$.rating.stars").value(5))
                .andExpect(jsonPath("$.rating.comment").value("Lovely, still warm!"))
                .andExpect(jsonPath("$.rating.createdAt").exists());
    }

    @Test
    void r3_commentIsOptional() throws Exception {
        String token = customerToken();
        String number = deliveredOrder(token);

        rate(token, number, 3, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rating.stars").value(3))
                .andExpect(jsonPath("$.rating.comment").value(nullValue()));
    }

    @Test
    void unratedOrderHasNoRating() throws Exception {
        String token = customerToken();
        String number = deliveredOrder(token);

        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.rating").value(nullValue()));
    }

    // --- AC2: who and what can be rated -------------------------------------------------------

    @Test
    void ac2_r8_nonDeliveredOrdersCannotBeRated() throws Exception {
        String token = customerToken();
        String received = placeOrder(token, SHARED_CHEF_ID, SHARED_DISH_ID);
        String accepted = placeOrder(token, SHARED_CHEF_ID, SHARED_DISH_ID);
        adminSetStatus(adminToken(), accepted, "ACCEPTED");

        for (String number : List.of(received, accepted)) {
            rate(token, number, 5, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Only delivered orders can be reviewed."));
        }
    }

    @Test
    void ac2_r9_sameOrderCannotBeRatedTwice() throws Exception {
        String token = customerToken();
        String number = deliveredOrder(token);
        rate(token, number, 4, "first").andExpect(status().isOk());

        rate(token, number, 1, "second")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Order already reviewed."));

        // The first rating is untouched.
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.rating.stars").value(4))
                .andExpect(jsonPath("$.rating.comment").value("first"));
    }

    @Test
    void ac2_r7_anotherCustomersOrderLooksNotFound() throws Exception {
        String owner = customerToken();
        String number = deliveredOrder(owner);

        rate(customerToken(), number, 1, "not mine")
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found"));
        rate(customerToken(), "FM-999999", 1, null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Order not found"));

        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.rating").value(nullValue()));
    }

    // --- AC3: input validation ----------------------------------------------------------------

    @Test
    void ac3_starsMustBeAWholeNumberFrom1To5() throws Exception {
        String token = customerToken();
        String number = deliveredOrder(token);

        for (Object stars : new Object[]{0, 6, -1, 4.5, null}) {
            rate(token, number, stars, null)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.message").value("Stars must be a whole number from 1 to 5."));
        }
        mockMvc.perform(post("/api/customer/orders/" + number + "/rating")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"stars\": \"lots\"}"))
                .andExpect(status().isBadRequest());

        // None of the rejected attempts saved anything.
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.rating").value(nullValue()));
    }

    @Test
    void ac3_commentLongerThan1000CharactersIsRejected() throws Exception {
        String token = customerToken();
        String tooLong = deliveredOrder(token);
        String exactlyMax = deliveredOrder(token);

        rate(token, tooLong, 5, "x".repeat(1001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Comment must be at most 1000 characters."));
        rate(token, exactlyMax, 5, "x".repeat(1000)).andExpect(status().isOk());
    }

    // --- AC4: signed-in customers only --------------------------------------------------------

    @Test
    void ac4_notSignedInCannotRate() throws Exception {
        String number = deliveredOrder(customerToken());

        rate(null, number, 5, null).andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(jsonPath("$.rating").value(nullValue()));
    }

    // --- AC5: chef rating = average of customer ratings, one decimal --------------------------

    @Test
    void ac5_chefRatingIsTheRoundedAverageOfCustomerRatings() throws Exception {
        String token = customerToken();
        String first = deliveredOrder(token, AVERAGE_CHEF_ID, AVERAGE_DISH_ID);
        String second = deliveredOrder(token, AVERAGE_CHEF_ID, AVERAGE_DISH_ID);
        String third = deliveredOrder(token, AVERAGE_CHEF_ID, AVERAGE_DISH_ID);

        rate(token, first, 5, null).andExpect(status().isOk());
        // R10: the seeded 4.8 is replaced by real ratings as soon as the first one arrives.
        mockMvc.perform(get("/api/chef/" + AVERAGE_CHEF_ID)).andExpect(jsonPath("$.rating").value(5.0));

        rate(token, second, 4, null).andExpect(status().isOk());
        rate(token, third, 4, null).andExpect(status().isOk());

        // (5 + 4 + 4) / 3 = 4.333... -> 4.3
        mockMvc.perform(get("/api/chef/" + AVERAGE_CHEF_ID)).andExpect(jsonPath("$.rating").value(4.3));
    }
}
