package kg.chairx.security;
import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;
import java.util.UUID;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"CHAIRX_CATALOG_PASSWORD=integration-test-password","spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class F12PasswordResetIntegrationTests {
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 UUID admin,target;
 String adminLogin,targetLogin;
 static final String OLD="StrongOld123!",NEW="StrongNew123!";
 @BeforeEach void setup(){
  assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("chairx_test");
  admin=UUID.randomUUID();target=UUID.randomUUID();
  adminLogin="f12_pwd_admin_"+admin.toString().replace("-","");
  targetLogin="f12_pwd_employee_"+target.toString().replace("-","");
  String hash=PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(OLD);
  jdbc.update("INSERT INTO app_users(id,username,password_hash,display_name,active) VALUES(?,?,?,'Test Admin',true)",admin,adminLogin,hash);
  jdbc.update("INSERT INTO app_users(id,username,password_hash,display_name,active) VALUES(?,?,?,'Test Employee',true)",target,targetLogin,hash);
  jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES(?,'00000000-0000-0000-0000-000000000001')",admin);
  jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES(?,'00000000-0000-0000-0000-000000000003')",target);
 }
 @AfterEach void cleanup(){
  assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("chairx_test");
  jdbc.update("DELETE FROM security_audit_log WHERE actor_user_id=? OR target_id=?",admin,target);
  jdbc.update("DELETE FROM security_user_roles WHERE user_id IN (?,?)",admin,target);
  jdbc.update("DELETE FROM app_users WHERE id IN (?,?)",admin,target);
 }
 @Test void newHashAndAuditAreSafeAndOldLoginFails() throws Exception {
  mvc.perform(post("/api/admin/users/{id}/reset-password",target)
   .with(httpBasic(adminLogin,OLD)).with(csrf()).contentType(MediaType.APPLICATION_JSON)
   .content("{\"newPassword\":\""+NEW+"\"}"))
   .andExpect(status().isNoContent()).andExpect(content().string(""));
  String hash=jdbc.queryForObject("SELECT password_hash FROM app_users WHERE id=?",String.class,target);
  assertThat(hash).startsWith("{bcrypt}").doesNotContain(NEW);
  var encoder=PasswordEncoderFactories.createDelegatingPasswordEncoder();
  assertThat(encoder.matches(OLD,hash)).isFalse();
  assertThat(encoder.matches(NEW,hash)).isTrue();
  mvc.perform(get("/api/auth/me").with(httpBasic(targetLogin,OLD))).andExpect(status().isUnauthorized());
  mvc.perform(get("/api/auth/me").with(httpBasic(targetLogin,NEW))).andExpect(status().isOk());
  String audit=jdbc.queryForObject("SELECT details FROM security_audit_log WHERE target_id=? AND action='USER_PASSWORD_RESET'",String.class,target);
  assertThat(audit).doesNotContain(OLD,NEW,hash);
 }
}
