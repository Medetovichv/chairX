package kg.chairx.security;

import kg.chairx.PostgresTestConfiguration;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.test.web.servlet.MockMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"CHAIRX_CATALOG_PASSWORD=integration-test-password",
 "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/never_use_local"})
@AutoConfigureMockMvc
@Import(PostgresTestConfiguration.class)
class F12EmployeeActivationIntegrationTests {
 static final UUID ADMIN=UUID.fromString("00000000-0000-0000-0000-000000000001");
 static final UUID EMPLOYEE=UUID.fromString("00000000-0000-0000-0000-000000000003");
 static final UUID MANAGER=UUID.fromString("00000000-0000-0000-0000-000000000002");
 static final String PASSWORD="F12-integration-password";
 @Autowired MockMvc mvc;
 @Autowired JdbcTemplate jdbc;
 final List<UUID> created=new ArrayList<>();
 record Account(UUID id,String login){}
 @BeforeEach void guard(){assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("chairx_test");}
 Account create(UUID role,boolean active){
   UUID id=UUID.randomUUID();created.add(id);
   String login="f12_act_"+id.toString().replace("-","");
   jdbc.update("INSERT INTO app_users(id,username,password_hash,display_name,active) VALUES(?,?,?,?,?)",
     id,login,PasswordEncoderFactories.createDelegatingPasswordEncoder().encode(PASSWORD),"F12 test",active);
   if(role!=null)jdbc.update("INSERT INTO security_user_roles(user_id,role_id) VALUES(?,?)",id,role);
   return new Account(id,login);
 }
 @AfterEach void cleanup(){
   assertThat(jdbc.queryForObject("SELECT current_database()",String.class)).isEqualTo("chairx_test");
   for(UUID id:created){
     jdbc.update("DELETE FROM security_audit_log WHERE actor_user_id=? OR target_id=?",id,id);
     jdbc.update("DELETE FROM security_user_roles WHERE user_id=?",id);
     jdbc.update("DELETE FROM app_users WHERE id=?",id);
   }
 }
 @Test void activatesExistingUserOnceAndRetainsUuidRolesAndHistory() throws Exception {
   Account admin=create(ADMIN,true),target=create(EMPLOYEE,false);
   jdbc.update("INSERT INTO security_audit_log(id,actor_user_id,action,target_type,target_id,details) VALUES(?,?,'HISTORIC','USER',?,'keep')",
     UUID.randomUUID(),admin.id(),target.id());
   mvc.perform(patch("/api/admin/users/{id}/activate",target.id())
       .with(httpBasic(admin.login(),PASSWORD)).with(csrf()))
      .andExpect(status().isNoContent());
   mvc.perform(patch("/api/admin/users/{id}/activate",target.id())
       .with(httpBasic(admin.login(),PASSWORD)).with(csrf()))
      .andExpect(status().isNoContent());
   assertThat(jdbc.queryForObject("SELECT active FROM app_users WHERE id=?",Boolean.class,target.id())).isTrue();
   assertThat(jdbc.queryForObject("SELECT count(*) FROM security_user_roles WHERE user_id=? AND role_id=?",
     Long.class,target.id(),EMPLOYEE)).isEqualTo(1);
   assertThat(jdbc.queryForObject("SELECT count(*) FROM security_audit_log WHERE target_id=? AND action='USER_ACTIVATED'",
     Long.class,target.id())).isEqualTo(1);
   assertThat(jdbc.queryForObject("SELECT count(*) FROM security_audit_log WHERE target_id=? AND action='HISTORIC'",
     Long.class,target.id())).isEqualTo(1);
 }
 @Test void deniesNonAdminAndMissingCsrfAndMissingUser() throws Exception {
   Account manager=create(MANAGER,true),admin=create(ADMIN,true),target=create(EMPLOYEE,false);
   mvc.perform(patch("/api/admin/users/{id}/activate",target.id())
      .with(httpBasic(manager.login(),PASSWORD)).with(csrf()))
      .andExpect(status().isForbidden());
   mvc.perform(patch("/api/admin/users/{id}/activate",target.id())
      .with(httpBasic(admin.login(),PASSWORD)))
      .andExpect(status().isForbidden());
   mvc.perform(patch("/api/admin/users/{id}/activate",UUID.randomUUID())
      .with(httpBasic(admin.login(),PASSWORD)).with(csrf()))
      .andExpect(status().isNotFound());
   assertThat(jdbc.queryForObject("SELECT active FROM app_users WHERE id=?",Boolean.class,target.id())).isFalse();
 }
}
