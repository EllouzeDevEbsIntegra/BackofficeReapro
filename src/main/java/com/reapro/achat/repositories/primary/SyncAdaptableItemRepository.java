package com.reapro.achat.repositories.primary;

import com.reapro.achat.entities.primary.SyncAdaptableItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface SyncAdaptableItemRepository extends JpaRepository<SyncAdaptableItem, Long>, JpaSpecificationExecutor<SyncAdaptableItem> {

    String MASTER_OEM_FILTER = "(s.oem IS NULL OR UPPER(s.oem) NOT LIKE 'MASTER%')";

    @Query("SELECT DISTINCT s.tdBrandId as code, s.tdBrandName as label FROM SyncAdaptableItem s WHERE s.tdBrandId IS NOT NULL AND s.tdBrandName IS NOT NULL AND s.tdBrandName <> '' AND " + MASTER_OEM_FILTER + " ORDER BY s.tdBrandName")
    List<BrandProjection> findDistinctBrands();

    @Query("SELECT DISTINCT s.partGroupCode as code, s.partGroupName as label FROM SyncAdaptableItem s WHERE s.partGroupCode IS NOT NULL AND s.partGroupCode <> '' AND " + MASTER_OEM_FILTER + " ORDER BY s.partGroupName")
    List<CodeLabelProjection> findDistinctGroups();

    @Query("SELECT DISTINCT s.partSubgroupCode as code, s.partSubgroupName as label, s.partGroupCode as parentCode FROM SyncAdaptableItem s WHERE s.partSubgroupCode IS NOT NULL AND s.partSubgroupCode <> '' AND " + MASTER_OEM_FILTER + " ORDER BY s.partSubgroupName")
    List<SubGroupProjection> findDistinctSubGroups();

    @Query("SELECT DISTINCT s.master FROM SyncAdaptableItem s WHERE s.master IS NOT NULL AND s.master <> '' AND " + MASTER_OEM_FILTER + " ORDER BY s.master")
    List<String> findDistinctMasters();

    @Query("SELECT COUNT(s) FROM SyncAdaptableItem s WHERE " + MASTER_OEM_FILTER)
    long countExcludingMasterOem();

    @Query("SELECT COUNT(DISTINCT s.master) FROM SyncAdaptableItem s WHERE s.master IS NOT NULL AND s.master <> '' AND " + MASTER_OEM_FILTER)
    long countDistinctMaster();

    @Query("SELECT COUNT(DISTINCT s.partSubgroupCode) FROM SyncAdaptableItem s WHERE s.partSubgroupCode IS NOT NULL AND s.partSubgroupCode <> '' AND " + MASTER_OEM_FILTER)
    long countDistinctSubGroup();

    @Query("SELECT COUNT(DISTINCT s.tdBrandName) FROM SyncAdaptableItem s WHERE s.tdBrandName IS NOT NULL AND s.tdBrandName <> '' AND " + MASTER_OEM_FILTER)
    long countDistinctBrand();

    @Query("SELECT COUNT(DISTINCT s.partGroupCode) FROM SyncAdaptableItem s WHERE s.partGroupCode IS NOT NULL AND s.partGroupCode <> '' AND " + MASTER_OEM_FILTER)
    long countDistinctGroup();

    interface CodeLabelProjection {
        String getCode();
        String getLabel();
    }

    interface BrandProjection {
        Integer getCode();
        String getLabel();
    }

    interface SubGroupProjection {
        String getCode();
        String getLabel();
        String getParentCode();
    }
}
