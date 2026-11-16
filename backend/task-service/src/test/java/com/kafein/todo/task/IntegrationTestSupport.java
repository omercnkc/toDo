package com.kafein.todo.task;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
import com.kafein.todo.task.client.ProjectClient;
import com.kafein.todo.task.client.ProjectMembership;
import com.kafein.todo.task.client.ProjectRole;
import com.kafein.todo.task.client.ProjectStatus;
import com.kafein.todo.task.client.ProjectSummary;
import com.kafein.todo.task.task.TaskRepository;
import feign.FeignException;
import feign.Request;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Entegrasyon testlerinin ortak tabanı: gerçek güvenlik zinciri + gerçek MySQL, project-service yerine
 * bellek içi sahte bir {@link ProjectClient}. Tüm alt sınıflar aynı Spring context'ini paylaşır.
 * <p>
 * Testler aynı veritabanını kullandığı için her test kendine özgü kullanıcı ve proje id'leriyle çalışır.
 * Sahte project-service: {@link #newProject} ile açılan projeleri ve üyelerini bilir; olmayan üyelik/proje için
 * gerçeği gibi 404 döner.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {

    protected static final String SECRET = "test-secret-test-secret-test-secret-123"; // application-test.yml
    private static final AtomicLong IDS = new AtomicLong(1_000);
    private static final JwtService JWT = new JwtService(SECRET, Duration.ofMinutes(15));
    private static final Request FEIGN_REQUEST = Request.create(Request.HttpMethod.GET, "http://project-service",
            Map.of(), null, StandardCharsets.UTF_8, null);

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected TaskRepository tasks;
    @MockBean
    protected ProjectClient projectClient;

    /** projectId → (userId → rol). */
    private final Map<Long, Map<Long, ProjectRole>> memberships = new ConcurrentHashMap<>();
    private final Map<Long, ProjectStatus> projectStatuses = new ConcurrentHashMap<>();

    protected AuthenticatedUser owner;
    protected AuthenticatedUser member;
    protected AuthenticatedUser member2;
    protected AuthenticatedUser stranger;
    protected AuthenticatedUser admin;

    @BeforeEach
    void setUpUsersAndProjectService() {
        owner = newUser(Role.USER);
        member = newUser(Role.USER);
        member2 = newUser(Role.USER);
        stranger = newUser(Role.USER);
        admin = newUser(Role.ADMIN);

        when(projectClient.membership(anyLong(), anyLong())).thenAnswer(inv -> {
            long projectId = inv.getArgument(0);
            long userId = inv.getArgument(1);
            ProjectRole role = memberships.getOrDefault(projectId, Map.of()).get(userId);
            if (role == null) {
                throw new FeignException.NotFound("MEMBERSHIP_NOT_FOUND", FEIGN_REQUEST, null, Map.of());
            }
            return new ProjectMembership(projectId, userId, role, projectStatuses.get(projectId));
        });
        when(projectClient.project(anyLong())).thenAnswer(inv -> {
            long projectId = inv.getArgument(0);
            ProjectStatus status = projectStatuses.get(projectId);
            if (status == null) {
                throw new FeignException.NotFound("PROJECT_NOT_FOUND", FEIGN_REQUEST, null, Map.of());
            }
            return new ProjectSummary(projectId, "Proje " + projectId, status);
        });
    }

    protected static AuthenticatedUser newUser(Role role) {
        long id = IDS.incrementAndGet();
        return new AuthenticatedUser(id, "kullanici" + id + "@ornek.com", role);
    }

    /** {@code owner} OWNER, {@code member} ve {@code member2} MEMBER olan aktif proje. */
    protected long newProject() {
        long projectId = IDS.incrementAndGet();
        Map<Long, ProjectRole> roles = new HashMap<>();
        roles.put(owner.id(), ProjectRole.OWNER);
        roles.put(member.id(), ProjectRole.MEMBER);
        roles.put(member2.id(), ProjectRole.MEMBER);
        memberships.put(projectId, roles);
        projectStatuses.put(projectId, ProjectStatus.ACTIVE);
        return projectId;
    }

    protected void archive(long projectId) {
        projectStatuses.put(projectId, ProjectStatus.ARCHIVED);
    }

    protected static String bearer(AuthenticatedUser user) {
        return "Bearer " + JWT.generateAccessToken(user);
    }

    /** project-service outbox relay'inin kullanacağı servis token'ı. */
    protected static String serviceBearer() {
        return "Bearer " + JWT.generateServiceToken("project-service");
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, AuthenticatedUser as) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(as)));
    }

    protected ResultActions send(MockHttpServletRequestBuilder request, AuthenticatedUser as, Object body)
            throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)), as);
    }

    protected JsonNode json(ResultActions result) throws Exception {
        // Yanıtta charset yok (application/json): varsayılan ISO-8859-1 yerine UTF-8 okunur
        return objectMapper.readTree(result.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    /** {@code as} adına görev oluşturur, id'sini döndürür. */
    protected long createTask(long projectId, String title, AuthenticatedUser as) throws Exception {
        return json(send(post("/tasks"), as, Map.of("projectId", projectId, "title", title))
                .andExpect(status().isCreated())).get("id").asLong();
    }
}
