package com.smartpay.security;

import com.smartpay.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    //Secret Key
    private SecretKey getSignKey(){
        byte[] keyBytes = Decoders.BASE64.decode(secret);    // Decode the encoded secret and store it in the ASCII values i.e. [97,98,99,etc] as encryption is mathematical operation that's why it is stored in the ASCII values and cryptography is not work on the String
        return Keys.hmacShaKeyFor(keyBytes);
    }

    //generate token
    public String generateToken(UserDetails userDetails){

        return generateToken(new HashMap<>(), userDetails);
    }

    public String generateToken(User user){
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", user.getUserId());
        claims.put("role", user.getRole());

        return createToken(claims, user.getEmail());
    }

    public String createToken(Map<String, Object> claims, String subject){

        return Jwts.builder()
                .claims(claims)
                .subject(subject)
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignKey())
                .compact();
    }

    public String generateToken(Map<String, Object> extractClaims, UserDetails userDetails){
        return Jwts.builder()
                .claims(extractClaims)
                .subject(userDetails.getUsername())
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + jwtExpiration))
                .signWith(getSignKey(), Jwts.SIG.HS256)
                .compact();
    }

    public Long extractUserId(String token){
        Claims claims = extractAllClaims(token);
        return  claims.get("userId", Long.class);
    }

    // Extract Username
    public String extractUsername(String token){
        return extractClaim(token, Claims::getSubject);
    }

    //above code is exactly equivalent to the following code
//    public String extractUsername(String token){
//        return extractClaim(
//                token,
//                claims -> claims.getSubject()
//        );
//    }

    //Generic claim Extractor
    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver){
        Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    //Extract All Claims
    private Claims extractAllClaims(String token){
        JwtParser parser = Jwts.parser()
                .verifyWith(getSignKey())
                .build();

        return parser
                .parseSignedClaims(token)
                .getPayload();
    }

//    public boolean isTokenValid(String token, UserDetails userDetails){
//        String username = extractUsername(token);
//
//        return username.equals(userDetails.getUsername()) && !isTokenExpired(token);
//    }


    // above code and this code is same
    public boolean isTokenValid(String token, UserDetails userDetails){
        Claims claims = extractAllClaims(token);

        String username = claims.getSubject();
        Date expiration = claims.getExpiration();

        return username.equals(userDetails.getUsername()) && expiration.after(new Date());
    }

    private boolean isTokenExpired(String token){
        return extractClaim(token, Claims::getExpiration)
                .before(new Date());
    }
}
