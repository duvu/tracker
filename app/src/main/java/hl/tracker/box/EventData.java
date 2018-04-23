package hl.tracker.box;

import io.objectbox.annotation.Entity;
import io.objectbox.annotation.Id;

@Entity
public class EventData {
    @Id
    private Long id;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }
}
