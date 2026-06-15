package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Per-article identity row of the VOC "DB" sheet — the columns shown to the
 * left of the chemical matrix (model code col C, model name col D, and the
 * E/F base counts). The chemical dosages live in {@link VocStandardRate}.
 */
@Entity
@Table(name = "voc_recipe_article")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VocRecipeArticle {

    @Id
    @Column(name = "article_no", length = 40)
    private String articleNo;

    @Column(name = "model_code", length = 60)
    private String modelCode;

    @Column(name = "model_name", length = 160)
    private String modelName;

    @Column(name = "base_e")
    private Double baseE;

    @Column(name = "base_f")
    private Double baseF;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
