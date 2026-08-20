package com.teamproject.authorization.config;
import com.teamproject.user.domain.*;
import jakarta.servlet.*; import jakarta.servlet.http.*; import org.springframework.security.core.context.SecurityContextHolder; import org.springframework.stereotype.Component; import org.springframework.web.filter.OncePerRequestFilter; import java.io.IOException;
@Component public class ForcedPasswordChangeFilter extends OncePerRequestFilter {
 private final UserRepository users; public ForcedPasswordChangeFilter(UserRepository users){this.users=users;}
 protected void doFilterInternal(HttpServletRequest r,HttpServletResponse s,FilterChain c)throws ServletException,IOException{
  var a=SecurityContextHolder.getContext().getAuthentication(); String p=r.getRequestURI(); boolean allowed=p.endsWith("/user/password")||p.endsWith("/auth/me")||p.endsWith("/auth/logout")||p.endsWith("/auth/logout-all")||p.endsWith("/auth/refresh");
  if(a!=null&&a.isAuthenticated()&&a.getPrincipal() instanceof Long id&&users.findById(id).map(User::isForcePasswordChange).orElse(false)&&!allowed){s.setStatus(403);s.setContentType("application/json");s.getWriter().write("{\"code\":\"PASSWORD_CHANGE_REQUIRED\"}");return;} c.doFilter(r,s);
 }
}
