package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "features")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Feature {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "feature_key", unique = true, nullable = false, length = 64)
    private String featureKey;

    @Column(name = "display_name", nullable = false, length = 150)
    private String displayName;

    @Column(name = "url_patterns", nullable = false, length = 500)
    private String urlPatterns;

    @Column(name = "http_methods", length = 50)
    private String httpMethods;

    @Column(length = 50)
    private String category;

    @Column(nullable = false)
    private Integer priority;
}
