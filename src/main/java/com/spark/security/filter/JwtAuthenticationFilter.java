package com.spark.security.filter;

import com.spark.security.security.UserDetailsImpl;
import com.spark.security.utils.JwtUtils;
import com.spark.security.utils.RedisUtils;
import com.spark.security.utils.UserContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final RedisUtils redisUtils;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        String username = null;
        
        // 当 Header 中没有以 Bearer 开头的 Authorization 时直接放行
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        // 提取 JWT
        jwt = authHeader.substring(7);

        // 1. 检查 Token 是否在黑名单中
        if (redisUtils.hasKey("blacklist:token:" + jwt)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":401,\"message\":\"此Token已被拉黑，请重新登录\",\"data\":null}");
            return;
        }
        
        try {
            // 从 JWT 中提取用户名
            username = jwtUtils.extractUsername(jwt);

            // 当获取到了用户名并且尚未通过认证时
            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                // 2. 检查用户是否被封禁 (状态为 0)
                if (!userDetails.isEnabled()) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=utf-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"该账号已被封禁，请联系管理员\",\"data\":null}");
                    return;
                }

                // 3. 校验 Token 版本号 (pv)
                Long tokenPv = jwtUtils.extractPv(jwt);
                Long dbPv = 1L;
                if (userDetails instanceof UserDetailsImpl) {
                    Long userPv = ((UserDetailsImpl) userDetails).getUser().getPv();
                    if (userPv != null) {
                        dbPv = userPv;
                    }
                }

                if (tokenPv == null || !tokenPv.equals(dbPv)) {
                    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    response.setContentType("application/json;charset=utf-8");
                    response.getWriter().write("{\"code\":401,\"message\":\"登录凭证已失效，请重新登录\",\"data\":null}");
                    return;
                }

                // 验证 Token 是否有效
                if (jwtUtils.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    // 更新 SecurityContextHolder
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // 将UserDetails强转为我的实现类
                    if(userDetails instanceof UserDetailsImpl) {
                        // 存入用户信息
                        Long userId = ((UserDetailsImpl) userDetails).getUser().getId();
                        String userName = ((UserDetailsImpl) userDetails).getUser().getUsername();
                        UserContext.setUserInfo(userId, userName);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":401,\"message\":\"Token已过期，请使用RefreshToken刷新或重新登录\",\"data\":null}");
        } catch (Exception e) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=utf-8");
            response.getWriter().write("{\"code\":401,\"message\":\"无效的Token\",\"data\":null}");
        } finally {
            // 清理ThreadLocal
            UserContext.clear();
        }
    }
}