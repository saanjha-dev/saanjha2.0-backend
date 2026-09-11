import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.nio.charset.StandardCharsets;

public class TestJwts {
    public static void main(String[] args) {
        Map<String, Object> videoGrant = new HashMap<>();
        videoGrant.put("roomJoin", true);
        videoGrant.put("room", "room123");

        Map<String, Object> claims = new HashMap<>();
        claims.put("video", videoGrant);
        
        String token = Jwts.builder()
                .header().add("alg", "HS256").add("typ", "JWT").and()
                .issuer("api_key")
                .subject("user_id")
                .claims(claims)
                .expiration(new Date(System.currentTimeMillis() + 3600000))
                .signWith(Keys.hmacShaKeyFor("this_is_a_secret_key_that_is_long_enough".getBytes(StandardCharsets.UTF_8)), Jwts.SIG.HS256)
                .compact();
                
        System.out.println("Token: " + token);
    }
}
