package ru.practicum.ewm.main.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import ru.practicum.stats.client.StatsClient;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MainServiceApiTest {
    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StatsClient statsClient;

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
}
