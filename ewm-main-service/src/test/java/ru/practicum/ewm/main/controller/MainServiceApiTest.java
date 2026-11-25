package ru.practicum.ewm.main.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestExecutionListeners;
import org.springframework.test.context.support.DependencyInjectionTestExecutionListener;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import ru.practicum.ewm.main.repository.CategoryRepository;
import ru.practicum.ewm.main.repository.CommentRepository;
import ru.practicum.ewm.main.repository.CompilationRepository;
import ru.practicum.ewm.main.repository.EventRepository;
import ru.practicum.ewm.main.repository.ParticipationRequestRepository;
import ru.practicum.ewm.main.repository.UserRepository;
import ru.practicum.stats.client.StatsClient;
import ru.practicum.stats.dto.EndpointHit;
import ru.practicum.stats.dto.ViewStats;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MainServiceApiTest.StatisticsConfiguration.class)
@TestExecutionListeners(DependencyInjectionTestExecutionListener.class)
class MainServiceApiTest {
    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private CategoryRepository categories;

    @Autowired
    private CompilationRepository compilations;

    @Autowired
    private EventRepository events;

    @Autowired
    private ParticipationRequestRepository requests;

    @Autowired
    private UserRepository users;

    @Autowired
    private CommentRepository comments;

    @Autowired
    private TestStatsClient stats;

    @BeforeEach
    void clearData() {
        comments.deleteAll();
        compilations.deleteAll();
        requests.deleteAll();
        events.deleteAll();
        categories.deleteAll();
        users.deleteAll();
        stats.hits.clear();
        stats.readOnlyTransactions.clear();
    }

    @Test
    void shouldCreateAndGetCategory() throws Exception {
        mockMvc.perform(
                post("/admin/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Концерты\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name").value("Концерты"));

        mockMvc.perform(get("/categories").param("from", "0").param("size", "10")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Концерты"));
    }

    @Test
    void shouldRejectCategoryWithBlankName() throws Exception {
        mockMvc.perform(post("/admin/categories").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\" \"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("BAD_REQUEST"));
    }

    @ParameterizedTest
    @ValueSource(strings = { "/admin/users", "/admin/events", "/admin/comments", "/categories", "/compilations",
            "/events", "/events/1/comments", "/users/999/events", "/users/999/comments" })
    void shouldRejectInvalidPaginationAtController(String path) throws Exception {
        mockMvc.perform(get(path).param("from", "-1").param("size", "10"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("BAD_REQUEST"));
        mockMvc.perform(get(path).param("from", "0").param("size", "0"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.status").value("BAD_REQUEST"));
        mockMvc.perform(get(path).param("from", "0").param("size", "-1"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldPersistCategoryUpdateAndRollbackConflictingName() throws Exception {
        long first = create("/admin/categories", Map.of("name", "First")).path("id").asLong();
        create("/admin/categories", Map.of("name", "Second"));
        mockMvc.perform(patch("/admin/categories/" + first).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("name", "Second"))))
                .andExpect(status().isConflict());
        assertThat(read("/categories/" + first).path("name").asText()).isEqualTo("First");
        change("/admin/categories/" + first, Map.of("name", "Updated"));
        assertThat(read("/categories/" + first).path("name").asText()).isEqualTo("Updated");
    }

    @Test
    void shouldPersistEventPublicationConfirmationAndCancellationWithoutExplicitSave() throws Exception {
        long owner = createUser("owner");
        long requester = createUser("requester");
        long category = create("/admin/categories", Map.of("name", "Concerts")).path("id").asLong();
        long event = createEvent(owner, category);
        change("/users/" + owner + "/events/" + event, Map.of("title", "Updated title"));
        assertThat(read("/users/" + owner + "/events/" + event).path("title").asText())
                .isEqualTo("Updated title");
        change("/admin/events/" + event, Map.of("stateAction", "PUBLISH_EVENT"));
        JsonNode published = read("/events/" + event);
        assertThat(published.path("state").asText()).isEqualTo("PUBLISHED");
        assertThat(published.path("publishedOn").isNull()).isFalse();
        assertThat(stats.hits).anySatisfy(hit -> assertThat(hit.getUri()).isEqualTo("/events/" + event));

        long request = create("/users/" + requester + "/requests?eventId=" + event, null).path("id").asLong();
        assertThat(read("/users/" + requester + "/requests").get(0).path("status").asText()).isEqualTo("PENDING");
        change("/users/" + owner + "/events/" + event + "/requests",
                Map.of("requestIds", List.of(request), "status", "CONFIRMED"));
        assertThat(read("/events/" + event).path("confirmedRequests").asLong()).isEqualTo(1);
        assertThat(read("/users/" + requester + "/requests").get(0).path("status").asText()).isEqualTo("CONFIRMED");

        change("/users/" + requester + "/requests/" + request + "/cancel", null);
        assertThat(read("/users/" + requester + "/requests").get(0).path("status").asText()).isEqualTo("CANCELED");
        assertThat(read("/events/" + event).path("confirmedRequests").asLong()).isZero();
    }

    @Test
    void shouldPersistCompilationFieldsAndEventMembershipWithoutExplicitSave() throws Exception {
        long owner = createUser("owner");
        long category = create("/admin/categories", Map.of("name", "Concerts")).path("id").asLong();
        long event = createEvent(owner, category);
        long compilation = create("/admin/compilations", Map.of("title", "Original", "events", List.of(event)))
                .path("id").asLong();
        assertThat(read("/compilations/" + compilation).path("events").size()).isEqualTo(1);
        change("/admin/compilations/" + compilation,
                Map.of("title", "Changed", "pinned", true, "events", List.of()));
        JsonNode changed = read("/compilations/" + compilation);
        assertThat(changed.path("title").asText()).isEqualTo("Changed");
        assertThat(changed.path("pinned").asBoolean()).isTrue();
        assertThat(changed.path("events").size()).isZero();
    }

    @Test
    void shouldRollbackEventChangesWhenPublicationIsRejected() throws Exception {
        long owner = createUser("owner");
        long category = create("/admin/categories", Map.of("name", "Concerts")).path("id").asLong();
        long event = createEvent(owner, category);
        change("/admin/events/" + event, Map.of("stateAction", "PUBLISH_EVENT"));
        mockMvc.perform(patch("/admin/events/" + event).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("title", "Invalid change", "stateAction", "REJECT_EVENT"))))
                .andExpect(status().isConflict());
        assertThat(read("/events/" + event).path("title").asText()).isEqualTo("Original title");
    }

    @Test
    void shouldPublishRejectAndHideEventComments() throws Exception {
        long owner = createUser("owner");
        long author = createUser("author");
        long stranger = createUser("stranger");
        long category = create("/admin/categories", Map.of("name", "Talks")).path("id").asLong();
        long event = createEvent(owner, category);

        mockMvc.perform(post("/users/" + author + "/comments").contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("text", "Too early", "eventId", event))))
                .andExpect(status().isConflict());
        change("/admin/events/" + event, Map.of("stateAction", "PUBLISH_EVENT"));
        mockMvc.perform(post("/users/" + author + "/comments").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\" \",\"eventId\":" + event + "}"))
                .andExpect(status().isBadRequest());

        long comment = create("/users/" + author + "/comments",
                Map.of("text", "Great event, looking forward to it", "eventId", event)).path("id").asLong();
        assertThat(read("/users/" + author + "/comments").get(0).path("status").asText()).isEqualTo("PENDING");
        mockMvc.perform(get("/comments/" + comment)).andExpect(status().isNotFound());
        assertThat(read("/events/" + event + "/comments").size()).isZero();

        JsonNode published = change("/admin/comments/" + comment, Map.of("status", "PUBLISHED"));
        assertThat(published.path("status").asText()).isEqualTo("PUBLISHED");
        assertThat(published.path("publishedOn").isNull()).isFalse();
        assertThat(read("/comments/" + comment).path("author").path("id").asLong()).isEqualTo(author);
        assertThat(read("/events/" + event + "/comments").get(0).path("text").asText())
                .isEqualTo("Great event, looking forward to it");

        change("/users/" + author + "/comments/" + comment,
                Map.of("text", "Updated comment after publication for review"));
        mockMvc.perform(get("/comments/" + comment)).andExpect(status().isNotFound());
        change("/admin/comments/" + comment, Map.of("status", "REJECTED"));
        assertThat(read("/events/" + event + "/comments").size()).isZero();
        mockMvc.perform(patch("/users/" + stranger + "/comments/" + comment).contentType(MediaType.APPLICATION_JSON)
                        .content(mapper.writeValueAsString(Map.of("text", "I should not edit this comment at all"))))
                .andExpect(status().isNotFound());
        mockMvc.perform(patch("/admin/comments/" + comment).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"UNKNOWN\"}")).andExpect(status().isBadRequest());

        mockMvc.perform(delete("/users/" + author + "/comments/" + comment)).andExpect(status().isNoContent());
        long removedByAdmin = create("/users/" + author + "/comments",
                Map.of("text", "Another comment that admin will remove", "eventId", event)).path("id").asLong();
        mockMvc.perform(delete("/admin/comments/" + removedByAdmin)).andExpect(status().isNoContent());
        mockMvc.perform(get("/admin/comments").param("events", String.valueOf(event)).param("states", "PENDING"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(delete("/admin/comments/" + removedByAdmin)).andExpect(status().isNotFound());
    }

    @Test
    void shouldUseReadOnlyTransactionsForEventReadsAndWriteTransactionsForCreation() throws Exception {
        long owner = createUser("owner");
        long category = create("/admin/categories", Map.of("name", "Concerts")).path("id").asLong();
        long event = createEvent(owner, category);
        assertThat(stats.readOnlyTransactions).containsExactly(false);
        stats.readOnlyTransactions.clear();
        read("/users/" + owner + "/events/" + event);
        read("/users/" + owner + "/events");
        assertThat(stats.readOnlyTransactions).containsExactly(true, true);
    }

    private long createUser(String name) throws Exception {
        return create("/admin/users", Map.of("name", name, "email", name + "@example.com")).path("id").asLong();
    }

    private long createEvent(long owner, long category) throws Exception {
        return create("/users/" + owner + "/events", Map.of(
                "title", "Original title",
                "annotation", "An annotation longer than twenty characters",
                "description", "A description longer than twenty characters",
                "category", category,
                "eventDate", LocalDateTime.now().plusDays(3).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                "location", Map.of("lat", 55.0, "lon", 37.0),
                "participantLimit", 1,
                "requestModeration", true)).path("id").asLong();
    }

    private JsonNode create(String path, Object body) throws Exception {
        return mapper.readTree(mockMvc.perform(post(path).contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "" : mapper.writeValueAsString(body)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode change(String path, Object body) throws Exception {
        return mapper.readTree(mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON)
                        .content(body == null ? "" : mapper.writeValueAsString(body)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode read(String path) throws Exception {
        return mapper.readTree(mockMvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
    }

    @TestConfiguration
    static class StatisticsConfiguration {
        @Bean
        @Primary
        TestStatsClient testStatsClient() {
            return new TestStatsClient();
        }
    }

    static class TestStatsClient extends StatsClient {
        private final List<EndpointHit> hits = new ArrayList<>();

        private final List<Boolean> readOnlyTransactions = new ArrayList<>();

        TestStatsClient() {
            super("http://localhost:9090");
        }

        @Override
        public void saveHit(EndpointHit hit) {
            hits.add(hit);
        }

        @Override
        public List<ViewStats> getStats(LocalDateTime start, LocalDateTime end, List<String> uris, boolean unique) {
            readOnlyTransactions.add(TransactionSynchronizationManager.isCurrentTransactionReadOnly());
            return List.of();
        }
    }
}
