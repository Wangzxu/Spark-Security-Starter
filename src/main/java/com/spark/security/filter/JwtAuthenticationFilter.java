package com.spark.security.filter;

import com.spark.security.security.UserDetailsImpl;
import com.spark.security.service.BlacklistService;
import com.spark.security.utils.JwtUtils;
import com.spark.security.utils.RedisUtils;
import com.spark.security.utils.UserContext;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtils jwtUtils;
    private final UserDetailsService userDetailsService;
    private final BlacklistService blacklistService;
    private final RedisUtils redisUtils;

    @Value("${jwt.expiration}")
    private long accessTokenExpiration;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        final String jwt;
        String username = null;
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }
        
        jwt = authHeader.substring(7);

        try {
            // 1. 检查 Token 是否在黑名单中
            if (blacklistService.isTokenBanned(jwt)) {
                log.debug("此Token已被拉黑");
                sendErrorResponse(response, "此Token已被拉黑，请重新登录");
                return;
            }

            username = jwtUtils.extractUsername(jwt);

            if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
                UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

                // 2. 检查用户是否被封禁 (状态为 0)
                if (!userDetails.isEnabled()) {
                    log.debug("该账号被封禁");
                    sendErrorResponse(response, "该账号已被封禁，请联系管理员");
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
                    sendErrorResponse(response, "登录凭证已失效，请重新登录");
                    return;
                }

                // 4. 校验单点登录 (检查 Redis 中的 JTI 是否与当前 Token 一致)
                if (userDetails instanceof UserDetailsImpl) {
                    log.debug("开始校验单点登录");
                    Long userId = ((UserDetailsImpl) userDetails).getUser().getId();
                    String clientType = "PC"; // 目前默认为 PC
                    String userAccessKey = "access_token:" + userId + ":" + clientType;
                    
                    Object currentJtiObj = redisUtils.get(userAccessKey);
                    String tokenJti = jwtUtils.extractJti(jwt);

                    if (currentJtiObj != null) {
                        String currentJti = (String) currentJtiObj;

                        if (!currentJti.equals(tokenJti)) {
                            // JTI 不一致，说明该用户已在其他地方登录
                            log.debug("开始拦截旧账号");
                            sendErrorResponse(response, "您的账号已在其他设备登录，如非本人操作请立即修改密码");
                            log.info("拦截逻辑执行完毕");
                            return;
                        }
                    } else {
                        log.debug("redis中不存在access token记录");
                        // Redis 中没有记录，可能是 Key 过期了或者被清理了。
                        // 3. 保存新的 Access Token JTI 到 Redis，有效期与 Token 一致
                        redisUtils.set(userAccessKey,tokenJti, accessTokenExpiration / 1000);
                    }
                }

                if (jwtUtils.isTokenValid(jwt, userDetails)) {
                    UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );
                    authToken.setDetails(
                            new WebAuthenticationDetailsSource().buildDetails(request)
                    );
                    SecurityContextHolder.getContext().setAuthentication(authToken);

                    // 存入用户信息到 ThreadLocal
                    if(userDetails instanceof UserDetailsImpl) {
                        Long userId = ((UserDetailsImpl) userDetails).getUser().getId();
                        String userName = ((UserDetailsImpl) userDetails).getUser().getUsername();
                        UserContext.setUserInfo(userId, userName);
                    }
                }
            }
            filterChain.doFilter(request, response);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            sendErrorResponse(response, "Token已过期，请使用RefreshToken刷新或重新登录");
        } catch (Exception e) {
            log.error("JWT 认证过滤器异常", e);
            sendErrorResponse(response, "无效的Token");
        } finally {
            UserContext.clear();
        }
    }

    private void sendErrorResponse(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json;charset=utf-8");
        response.getWriter().write("{\"code\":401,\"message\":\"" + message + "\",\"data\":null}");
    }
}