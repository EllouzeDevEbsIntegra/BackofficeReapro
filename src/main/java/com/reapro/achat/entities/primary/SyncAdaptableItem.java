package com.reapro.achat.entities.primary;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "sync_adaptable_item")
@Data
public class SyncAdaptableItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ext_id")
    private Long extId;

    @Column(name = "td_ref")
    private String tdRef;

    @Column(name = "td_brand_id")
    private Integer tdBrandId;

    @Column(name = "td_brand_name")
    private String tdBrandName;

    @Column(name = "td_description", length = 500)
    private String tdDescription;

    @Column(name = "oem")
    private String oem;

    @Column(name = "description", length = 500)
    private String description;

    @Column(name = "master")
    private String master;

    @Column(name = "part_make_code")
    private String partMakeCode;

    @Column(name = "part_group_code")
    private String partGroupCode;

    @Column(name = "part_group_name")
    private String partGroupName;

    @Column(name = "part_subgroup_code")
    private String partSubgroupCode;

    @Column(name = "part_subgroup_name")
    private String partSubgroupName;

    @Column(name = "champs_libre")
    private String champsLibre;
}
