package com.kafein.todo.project;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafein.todo.common.security.AuthenticatedUser;
import com.kafein.todo.common.security.JwtService;
import com.kafein.todo.common.security.Role;
import com.kafein.todo.project.client.AuthClient;
import com.kafein.todo.project.client.UserSummary;
import com.kafein.todo.project.member.ProjectMember;
import com.kafein.todo.project.member.ProjectMemberRepository;
import com.kafein.todo.project.project.ProjectRepository;
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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Entegrasyon testlerinin ortak tabanı: gerçek güvenlik zinciri + gerçek MySQL, auth-service yerine mock
 * {@link AuthClient}. Tüm alt sınıflar aynı Spring context'ini (ve aynı MySQL konteynerini) paylaşır.
 * <p>
 * Testler aynı veritabanını kullandığı için her test kendine özgü kullanıcı id'leriyle çalışır.
 * Mock auth-service, istenen her id için "Kullanıcı {id}" adlı aktif kullanıcı döndürür.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
public abstract class IntegrationTestSupport {

    protected static final String SECRET = "test-secret-test-secret-test-secret-123"; // application-test.yml
    private static final AtomicLong IDS = new AtomicLong(1_000);
    private static final JwtService JWT = new JwtService(SECRET, Duration.ofMinutes(15));

    @Autowired
    protected MockMvc mvc;
    @Autowired
    protected ObjectMapper objectMapper;
    @Autowired
    protected ProjectRepository projects;
    @Autowired
    protected ProjectMemberRepository members;
    @MockBean
    protected AuthClient authClient;

    protected AuthenticatedUser owner;
    protected AuthenticatedUser member;
    protected AuthenticatedUser stranger;
    protected AuthenticatedUser admin;

    @BeforeEach
    void setUpUsersAndAuthService() {
        owner = newUser(Role.USER);
        member = newUser(Role.USER);
        stranger = newUser(Role.USER);
        admin = newUser(Role.ADMIN);

        when(authClient.get(anyLong())).thenAnswer(inv -> fakeUser(inv.getArgument(0)));
        when(authClient.lookup(any())).thenAnswer(inv -> {
            AuthClient.LookupRequest request = inv.getArgument(0);
            return request.ids().stream().map(IntegrationTestSupport::fakeUser).toList();
        });
    }

    protected static AuthenticatedUser newUser(Role role) {
        long id = IDS.incrementAndGet();
        return new AuthenticatedUser(id, "kullanici" + id + "@ornek.com", role);
    }

    protected static UserSummary fakeUser(long id) {
        return new UserSummary(id, "kullanici" + id + "@ornek.com", "Kullanıcı " + id, true);
    }

    protected static String bearer(AuthenticatedUser user) {
        return "Bearer " + JWT.generateAccessToken(user);
    }

    protected ResultActions perform(MockHttpServletRequestBuilder request, AuthenticatedUser as) throws Exception {
        return mvc.perform(request.header(HttpHeaders.AUTHORIZATION, bearer(as)));
    }

    protected ResultActions send(MockHttpServletRequestBuilder request, AuthenticatedUser as, Object body)
            throws Exception {
        return perform(request.contentType(MediaType.APPLICATION_JSON).content(objectMapper.writeValueAsString(body)), as);
    }

    /** {@code owner} adına proje oluşturur ve id'sini döndürür; istenirse {@code member}'ı MEMBER olarak ekler. */
    protected long createProject(String name, boolean withMember) throws Exception {
        String json = send(post("/projects"), owner, Map.of("name", name, "description", "Açıklama"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        long id = objectMapper.readTree(json).get("id").asLong();
        if (withMember) {
            addMember(id, member.id());
        }
        return id;
    }

    protected void addMember(long projectId, long userId) {
        members.saveAndFlush(ProjectMember.member(projects.findById(projectId).orElseThrow(), userId));
    }
}
