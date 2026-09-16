package d6nggyun.stay.api;

import d6nggyun.stay.application.sync.CatalogSyncService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SyncController.class)
class SyncControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CatalogSyncService catalogSyncService;

    @Test
    void 수동_동기화_트리거는_동기화를_수행하고_200을_반환한다() throws Exception {
        mockMvc.perform(post("/api/v1/admin/catalog-sync"))
                .andExpect(status().isOk());

        verify(catalogSyncService, times(1)).syncAll();
    }
}
