// src/main/java/com/reapro/achat/entities/sqlserver/CompareQuoteLine.java

package com.reapro.achat.entities.sqlserver;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

@Entity
@Table(name = "View_ProjectReapro_ItemToBeCompared", schema = "dbo")
@Data
public class CompareQuoteLine {

    @Id
    @Column(name = "$systemId")
    private String systemId;

    @Column(name = "Compare Quote No_")
    private String compareQuoteNo;

    @Column(name = "No_")
    private String itemNo;

    @Column(name = "[Creation Date]")
    private LocalDateTime creationDate;

    @Column(name = "PageNumber")
    private Integer pageNumber;

    @Column(name = "[Description structurée]")
    private String structuredDescription;

    @Column(name = "[Count Item Manual]")
    private Integer countItemManual;

    @Column(name = "[NbLineNotThreated]")
    private Integer nbLineNotThreated;

    @Column(name = "Item Product Code")
    private String itemProductCode;

    @Column(name = "Item Sub Product Code")
    private String itemSubProductCode;

    @Column(name = "Groupe")
    private String groupe;

    @Column(name = "Sous Groupe")
    private String sousGroupe;

    @Column(name = "Champs libre")
    private String champsLibre;

    @Column(name = "Produit")
    private String produit;

    @Column(name = "Make Code")
    private String makeCode;

}