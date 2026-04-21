package thienloc.manage.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "new_style_entry")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NewStyleEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "data_month", nullable = false, length = 7)
    private String dataMonth;  // "2026-03"

    @Column(nullable = false, length = 20)
    private String section;

    @Column(nullable = false, length = 10)
    private String line;

    @Column(nullable = false, length = 50)
    private String style;

    @Column(nullable = false)
    private int quantity;
}
