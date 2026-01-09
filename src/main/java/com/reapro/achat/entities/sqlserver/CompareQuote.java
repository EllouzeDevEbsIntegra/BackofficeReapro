// src/main/java/com/reapro/achat/entities/sqlserver/CompareQuote.java

package com.reapro.achat.entities.sqlserver;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "View_ProjectReapro_CompareQuoteList", schema = "dbo")
@Data
public class CompareQuote {

    @Id
    @Column(name = "No_")
    private String no;

    @Column(name = "Status")
    private Integer status;

    @Column(name = "[User]")
    private String user;

    @Column(name = "[Creation Date]")
    private LocalDateTime creationDate;

    @Column(name = "[Modification Date]")
    private LocalDateTime modificationDate;

    @Column(name = "[No_ Series]")
    private String noSeries;

    @Column(name = "CompareType")
    private Integer compareType;

    @Column(name = "Description")
    private String description;

    @Column(name = "[$systemId]")
    private String systemId;
}
