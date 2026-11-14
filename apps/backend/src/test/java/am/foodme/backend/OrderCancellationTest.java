package am.foodme.backend;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * SCRUM-3: customers can cancel an order until the kitchen accepts it.
 * Test names reference the test-case IDs from the Jira ticket.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
// Fresh database afterwards, so the orders created here don't shift the order numbers
// that OrderControllerTest expects.
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class OrderCancellationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // --- helpers ------------------------------------------------------------------------------

    private String customerToken() throws Exception {
        String email = "cancel-" + UUID.randomUUID() + "@example.com";
        String response = mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "fullName", "Ann",
                                "email", email,
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

    /** Places an order and returns its number. New orders start in status NEW ("Received"). */
    private String placeOrder(String customerToken) throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "chefId", 1,
                "receiverName", "Ann",
                "receiverPhoneNumber", "+37491234567",
                "receiverEmail", "ann@example.com",
                "paymentType", "CASH",
                "deliveryMethod", "TAKEAWAY",
                "createOrderDishes", List.of(Map.of("dishId", 1, "quantity", 1))));
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
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("id").asLong();
    }

    private void adminSetStatus(String adminToken, long id, String newStatus, int expectedHttpStatus) throws Exception {
        mockMvc.perform(patch("/admin/order/" + id + "/status")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("status", newStatus))))
                .andExpect(status().is(expectedHttpStatus));
    }

    private void expectStatus(String number, String expected) throws Exception {
        mockMvc.perform(get("/api/order/number/" + number))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(expected));
    }

    private org.springframework.test.web.servlet.ResultActions cancel(String token, String number) throws Exception {
        var request = post("/api/customer/orders/" + number + "/cancel");
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return mockMvc.perform(request);
    }

    // --- A. Cancelling eligible orders --------------------------------------------------------

    @Test
    void tc01_customerCancelsReceivedOrder() throws Exception {
        String token = customerToken();
        String number = placeOrder(token);

        cancel(token, number)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.number").value(number))
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        expectStatus(number, "CANCELLED");
        mockMvc.perform(get("/api/customer/orders").header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.list[0].status").value("CANCELLED"));
    }

    @Test
    void tc04_acceptedOrderCannotBeCancelled() throws Exception {
        String token = customerToken();
        String number = placeOrder(token);
        adminSetStatus(adminToken(), orderId(number), "ACCEPTED", 200);

        cancel(token, number)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(
                        "This order can no longer be cancelled: the kitchen has already accepted it"));

        expectStatus(number, "ACCEPTED");
    }

    @Test
    void tc05_laterStatusesCannotBeCancelled() throws Exception {
        String admin = adminToken();
        String token = customerToken();

        String delivered = placeOrder(token);
        adminSetStatus(admin, orderId(delivered), "ACCEPTED", 200);
        adminSetStatus(admin, orderId(delivered), "DELIVERED", 200);

        String rejected = placeOrder(token);
        adminSetStatus(admin, orderId(rejected), "REJECTED", 200);

        cancel(token, delivered).andExpect(status().isConflict());
        cancel(token, rejected).andExpect(status().isConflict());
        expectStatus(delivered, "DELIVERED");
        expectStatus(rejected, "REJECTED");
    }

    @Test
    void tc06_cannotCancelAnotherCustomersOrder() throws Exception {
        String customerB = customerToken();
        String number = placeOrder(customerB);

        cancel(customerToken(), number).andExpect(status().isForbidden());

        expectStatus(number, "NEW");
    }

    @Test
    void tc07_notLoggedInIsRefused() throws Exception {
        String number = placeOrder(customerToken());

        cancel(null, number).andExpect(status().isUnauthorized());

        expectStatus(number, "NEW");
    }

    @Test
    void tc10_tc11_repeatedCancelIsHarmless() throws Exception {
        String token = customerToken();
        String number = placeOrder(token);

        cancel(token, number).andExpect(status().isOk());
        cancel(token, number)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }

    @Test
    void unknownOrderNumberIsNotFound() throws Exception {
        cancel(customerToken(), "FM-999999").andExpect(status().isNotFound());
    }

    // --- D. Cancelled is final ----------------------------------------------------------------

    @Test
    void tc21_tc22_tc23_cancelledOrderCannotMoveToAnyOtherStatus() throws Exception {
        String admin = adminToken();
        String token = customerToken();
        String number = placeOrder(token);
        cancel(token, number).andExpect(status().isOk());
        long id = orderId(number);

        for (String target : List.of("NEW", "ACCEPTED", "DELIVERED", "REJECTED")) {
            adminSetStatus(admin, id, target, 400);
        }
        expectStatus(number, "CANCELLED");
    }

    // --- F. Regression ------------------------------------------------------------------------

    @Test
    void tc29_teamCanStillCancelAReceivedOrderFromAdmin() throws Exception {
        String number = placeOrder(customerToken());

        adminSetStatus(adminToken(), orderId(number), "CANCELLED", 200);

        expectStatus(number, "CANCELLED");
    }

    @Test
    void tc30_normalFlowStillWorks() throws Exception {
        String admin = adminToken();
        String number = placeOrder(customerToken());
        long id = orderId(number);

        adminSetStatus(admin, id, "ACCEPTED", 200);
        adminSetStatus(admin, id, "DELIVERED", 200);

        expectStatus(number, "DELIVERED");
    }
}
