package com.ktb.chatapp.model;

import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rate_limits")
public class RateLimit {

    @Id
    private String id;

    private String clientId;

    private int count;

    private Instant expiresAt;
}
