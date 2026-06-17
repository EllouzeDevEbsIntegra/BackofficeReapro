package com.reapro.achat.partslink;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.openqa.selenium.*;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class PartslinkScraperService {

    private final PartslinkProperties properties;

    public record ScrapedVehicleDetails(
            String vin,
            String modelDesignation,
            String productionDate,
            String color,
            String upholstery,
            String transmission,
            String modelCode,
            String model,
            String brandCode,
            List<ScrapedGroup> groups
    ) {}

    public record ScrapedGroup(String code, String name) {}
    public record ScrapedSubgroup(String code, String name) {}
    public record ScrapedPart(
            String position,
            String partNumber,
            String designation,
            String infoSuppl,
            String quantity,
            String ae
    ) {}
    public record ScrapedSubgroupDetails(String imagePath, List<ScrapedPart> parts) {}

    /**
     * Identifies a vehicle by its VIN and extracts the main groups list.
     */
    public ScrapedVehicleDetails identifyVehicleAndGroups(RemoteWebDriver driver, String vin, String brand,
                                                          java.util.function.Consumer<String> stepListener) {
        log.info("[Scraper] Starting vehicle identification for VIN={} (brand={})", vin, brand);
        if (stepListener == null) {
            stepListener = step -> { };
        }
        if (driver == null) {
            throw new IllegalStateException("WebDriver non initialisé.");
        }
        try {
            driver.switchTo().defaultContent();

            // New PL24 UI: navigate directly to brand page instead of clicking old brand logos.
            if (StringUtils.hasText(brand)) {
                stepListener.accept("Selection de la marque : " + brand + "...");
                String brandUrl = "https://www.partslink24.com/pl24-app/" + brand + "/0/0?desktop=true&lang=fr";
                log.info("[Scraper] Navigate to brand URL: {}", brandUrl);
                driver.navigate().to(brandUrl);
                sleep(2000);
            }

            stepListener.accept("Recherche du champ VIN...");
            WebElement vinInput = findElementWithCandidates(driver, "VIN Input",
                    By.cssSelector("input#vin"),
                    By.cssSelector("input#vinSearch"),
                    By.cssSelector("input[name*=\"vin\"]"),
                    By.cssSelector("input[placeholder*=\"VIN\"]"),
                    By.cssSelector("input[placeholder*=\"Acc\"]"),
                    By.cssSelector("input[maxlength=\"17\"]"),
                    By.cssSelector("div[data-test-id=\"content\"] input[type=\"text\"]")
            );

            if (vinInput == null) {
                dumpDebug(driver, vin, "vin_input_not_found");
                throw new IllegalStateException("Impossible de trouver le champ de saisie du VIN sur la page.");
            }

            stepListener.accept("Saisie du VIN...");
            safeClick(driver, vinInput);
            vinInput.sendKeys(Keys.chord(Keys.CONTROL, "a"));
            vinInput.sendKeys(Keys.DELETE);
            vinInput.sendKeys(vin);
            sleep(300);

            stepListener.accept("Validation de la recherche VIN...");
            WebElement searchButton = findElementWithCandidates(driver, "VIN Search Button",
                    By.cssSelector("[data-test-id=\"sendVehicleSearch\"]"),
                    By.cssSelector(".icon--search-car"),
                    By.cssSelector(".MuiInputAdornment-root [class*=\"search\"]")
            );
            if (searchButton != null) {
                safeClick(driver, searchButton);
            } else {
                vinInput.sendKeys(Keys.ENTER);
            }

            stepListener.accept("Attente du chargement du catalogue...");
            boolean infoLoaded = false;
            for (int i = 0; i < 25; i++) {
                try {
                    Boolean hasInfo = (Boolean) driver.executeScript(
                        "return !!document.querySelector('.vehicle-info, .vehicle-data, #vehicle-details, [data-test-id=\"mainContainer\"], [data-test-id=\"content\"]');"
                    );
                    if (hasInfo != null && hasInfo) {
                        infoLoaded = true;
                        break;
                    }
                } catch (Exception ignored) {}
                sleep(300);
            }

            stepListener.accept("Extraction des informations vehicule...");
            WebElement infoPanel = findElementWithCandidates(driver, "Vehicle Info Panel",
                    By.cssSelector(".vehicle-info"),
                    By.cssSelector(".vehicle-data"),
                    By.cssSelector("#vehicle-details"),
                    By.cssSelector("[data-test-id=\"mainContainer\"]"),
                    By.cssSelector("[data-test-id=\"content\"]")
            );

            String panelText = infoPanel != null ? infoPanel.getText() : "";
            log.debug("[Scraper] Extracted vehicle panel text: {}", panelText);

            String modelDesignation = parseFieldByText(panelText, "Designation de modele|Designation|Model", vin);
            String productionDate = parseFieldByText(panelText, "Date de production|Production|Date", "Inconnue");
            String color = parseFieldByText(panelText, "Couleur|Color|Coloris", "Inconnue");
            String upholstery = parseFieldByText(panelText, "Sellerie|Upholstery|Interieur", "Inconnue");
            String transmission = parseFieldByText(panelText, "Transmission|Boite", "Inconnue");
            String modelCode = parseFieldByText(panelText, "Code-modele|Code modele|Model code", "Inconnu");
            String model = parseFieldByText(panelText, "Modele|Model", "Inconnu");

            if ("Inconnu".equals(model) && StringUtils.hasText(modelDesignation)) {
                model = modelDesignation;
            }

            stepListener.accept("Recuperation des groupes principaux...");
            List<ScrapedGroup> groups = extractMainGroups(driver);
            if (groups.isEmpty()) {
                dumpDebug(driver, vin, "groups_empty");
                throw new IllegalStateException("Aucun groupe principal trouve pour le vehicule.");
            }

            stepListener.accept("Extraction terminee.");
            // Capture the current brand code from the URL so we can navigate back later
            String resolvedBrand = StringUtils.hasText(brand) ? brand : extractBrandFromCurrentUrl(driver.getCurrentUrl());
            return new ScrapedVehicleDetails(
                    vin, modelDesignation, productionDate, color, upholstery, transmission, modelCode, model, resolvedBrand, groups
            );

        } catch (Exception e) {
            log.error("[Scraper] Failed to identify vehicle: {}", e.getMessage(), e);
            throw new IllegalStateException("Erreur lors de la recherche du VIN: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the subgroups for a selected main group.
     */
    public List<ScrapedSubgroup> fetchSubgroups(RemoteWebDriver driver, String vin, String groupCode, String brandCode) {
        log.info("[Scraper] Fetching subgroups for groupCode={} (VIN={}, brand={})", groupCode, vin, brandCode);
        if (driver == null) {
            throw new IllegalStateException("WebDriver non initialisé.");
        }
        try {
            driver.switchTo().defaultContent();

            // Always navigate to the vehicle page first to ensure mainGroupsTable is visible.
            // This handles the case where the browser has navigated away (e.g. after backend restart).
            String vehicleUrl = buildVehicleUrl(vin, brandCode, driver.getCurrentUrl());
            if (vehicleUrl != null) {
                String currentUrl = driver.getCurrentUrl();
                boolean alreadyOnVehiclePage = currentUrl != null
                        && currentUrl.contains(vin)
                        && currentUrl.contains("/vehicle");
                if (!alreadyOnVehiclePage) {
                    log.info("[Scraper] Navigating to vehicle page before fetching subgroups: {}", vehicleUrl);
                    driver.navigate().to(vehicleUrl);
                    sleep(2500);
                }
            }

            List<WebElement> rows = findElementsInAnyContext(driver, By.cssSelector("[data-test-id=\"mainGroupsTable\"] [data-test-id=\"row\"]"));
            WebElement targetGroupRow = findGroupRowByCode(rows, groupCode);

            if (targetGroupRow == null) {
                // Fallback: if current page is not the vehicle groups view, navigate back and retry once.
                String currentUrl = driver.getCurrentUrl();
                String extractedBrand = extractBrandFromCurrentUrl(currentUrl);
                if (StringUtils.hasText(extractedBrand)) {
                    String retryVehicleUrl = "https://www.partslink24.com/pl24-app/" + extractedBrand + "/" + vin + "/0/vehicle?desktop=true&lang=fr";
                    log.info("[Scraper] Group {} not found. Retrying after navigate to {}", groupCode, retryVehicleUrl);
                    driver.navigate().to(retryVehicleUrl);
                    sleep(2500);

                    rows = findElementsInAnyContext(driver, By.cssSelector("[data-test-id=\"mainGroupsTable\"] [data-test-id=\"row\"]"));
                    targetGroupRow = findGroupRowByCode(rows, groupCode);
                }
            }

            if (targetGroupRow == null) {
                dumpDebug(driver, vin + "_group_" + groupCode, "group_not_found");
                throw new IllegalStateException("Groupe principal '" + groupCode + "' introuvable.");
            }

            // Extract the initial codes from the table before clicking
            List<String> initialCodes = new ArrayList<>();
            try {
                List<Map<String, String>> initialRows = (List<Map<String, String>>) driver.executeScript(
                    "return Array.from(document.querySelectorAll('[data-test-id=\"mainGroupsTable\"] [data-test-id=\"row\"]')).map(row => {" +
                    "    const fgEl = row.querySelector('[data-test-id=\"fgValue\"] span');" +
                    "    const idEl = row.querySelector('[data-test-id=\"idValue\"] span');" +
                    "    return {" +
                    "        code: (fgEl ? fgEl.innerText.trim() : '') || (idEl ? idEl.innerText.trim() : '')" +
                    "    };" +
                    "}).filter(x => x.code);"
                );
                if (initialRows != null) {
                    for (Map<String, String> map : initialRows) {
                        String code = map.get("code");
                        if (code != null) {
                            initialCodes.add(code.trim().toLowerCase());
                        }
                    }
                }
            } catch (Exception ex) {
                log.warn("[Scraper] Failed to extract initial codes: {}", ex.getMessage());
            }

            safeClick(driver, targetGroupRow);

            // Wait dynamically for subgroups to load in the table
            boolean loaded = false;
            for (int attempt = 0; attempt < 15; attempt++) {
                try {
                    // 1. Check if the new subGroupsTable contains loaded rows (React/Vite style)
                    Boolean hasSubGroupsTable = (Boolean) driver.executeScript(
                        "let container = document.querySelector('[data-test-id=\"subGroupsTable\"]');" +
                        "if (container) {" +
                        "    let rows = container.querySelectorAll('[data-test-id=\"row\"]');" +
                        "    if (rows.length > 0) {" +
                        "        let desc = rows[0].querySelector('[data-test-id=\"descrValue\"]');" +
                        "        if (desc && desc.innerText.trim().length > 0) {" +
                        "            return true;" +
                        "        }" +
                        "    }" +
                        "}" +
                        "return false;"
                    );
                    if (hasSubGroupsTable != null && hasSubGroupsTable) {
                        loaded = true;
                        break;
                    }

                    // 2. Check if right-side table contains functional groups / illustration rows (BMW style)
                    Boolean hasRight = (Boolean) driver.executeScript(
                        "let table = document.querySelector('table#parts, table.parts-list, table.parts, .parts-table table, table[class*=\"part\"], table[class*=\"item\"]');" +
                        "if (!table) table = document.querySelector('table');" +
                        "if (!table) return false;" +
                        "let rows = Array.from(table.querySelectorAll('tr'));" +
                        "for (let row of rows) {" +
                        "    let cells = Array.from(row.querySelectorAll('td'));" +
                        "    if (cells.length < 2) continue;" +
                        "    if (cells[1].innerText.trim().match(/^([0-9a-zA-Z]+[_-][0-9a-zA-Z]+)/)) return true;" +
                        "}" +
                        "return false;"
                    );
                    if (hasRight != null && hasRight) {
                        loaded = true;
                        break;
                    }

                    // 3. Check if mainGroupsTable has changed (VW / Mercedes style)
                    List<Map<String, String>> currentRows = (List<Map<String, String>>) driver.executeScript(
                        "return Array.from(document.querySelectorAll('[data-test-id=\"mainGroupsTable\"] [data-test-id=\"row\"]')).map(row => {" +
                        "    const fgEl = row.querySelector('[data-test-id=\"fgValue\"] span');" +
                        "    const idEl = row.querySelector('[data-test-id=\"idValue\"] span');" +
                        "    return {" +
                        "        code: (fgEl ? fgEl.innerText.trim() : '') || (idEl ? idEl.innerText.trim() : '')" +
                        "    };" +
                        "}).filter(x => x.code);"
                    );

                    if (currentRows != null && !currentRows.isEmpty()) {
                        boolean isDifferent = false;
                        if (currentRows.size() != initialCodes.size()) {
                            isDifferent = true;
                        } else {
                            for (Map<String, String> row : currentRows) {
                                String code = row.get("code");
                                if (code == null) code = "";
                                code = code.trim().toLowerCase();
                                if (!initialCodes.contains(code)) {
                                    isDifferent = true;
                                    break;
                                }
                            }
                        }

                        if (isDifferent) {
                            loaded = true;
                            break;
                        }
                    }
                } catch (Exception ignored) {}
                sleep(300);
                if (attempt == 3 || attempt == 7) {
                    safeClick(driver, targetGroupRow);
                }
            }

            // Extract subgroups using JS for maximum speed (single network round-trip)
            List<ScrapedSubgroup> subgroups = new ArrayList<>();
            try {
                String jsCode = 
                    "let results = [];\n" +
                    "// 1. Try to extract from the new subGroupsTable (React/Vite UI layout)\n" +
                    "let subGroupsContainer = document.querySelector('[data-test-id=\"subGroupsTable\"]');\n" +
                    "if (subGroupsContainer) {\n" +
                    "    let rows = Array.from(subGroupsContainer.querySelectorAll('[data-test-id=\"row\"]'));\n" +
                    "    for (let row of rows) {\n" +
                    "        let descrEl = row.querySelector('[data-test-id=\"descrValue\"]');\n" +
                    "        let commentEl = row.querySelector('[data-test-id=\"commentValue\"]');\n" +
                    "        let fgEl = row.querySelector('[data-test-id=\"fgValue\"]');\n" +
                    "        let descText = descrEl ? descrEl.innerText.trim() : '';\n" +
                    "        if (descText) {\n" +
                    "            let code = '';\n" +
                    "            let label = descText;\n" +
                    "            let match = descText.match(/^([a-zA-Z0-9_-]+)\\s*[\\-–]\\s*(.*)$/);\n" +
                    "            if (match) {\n" +
                    "                code = match[1].trim();\n" +
                    "                label = match[2].trim();\n" +
                    "            } else {\n" +
                    "                let words = descText.split(/\\s+/);\n" +
                    "                if (words.length > 1 && words[0].match(/^[a-zA-Z0-9_-]+$/)) {\n" +
                    "                    code = words[0];\n" +
                    "                    label = descText.substring(code.length).trim();\n" +
                    "                }\n" +
                    "            }\n" +
                    "            let comment = commentEl ? commentEl.innerText.trim() : '';\n" +
                    "            if (comment) {\n" +
                    "                label += ' (' + comment + ')';\n" +
                    "            }\n" +
                    "            let fgText = fgEl ? fgEl.innerText.trim() : '';\n" +
                    "            if (fgText) {\n" +
                    "                label = fgText + ' - ' + label;\n" +
                    "            }\n" +
                    "            results.push({ code: code || descText, label: label });\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n" +
                    "// 2. Try to extract from traditional right-side table (BMW/GF style)\n" +
                    "if (results.length === 0) {\n" +
                    "    let table = document.querySelector('table#parts, table.parts-list, table.parts, .parts-table table, table[class*=\"part\"], table[class*=\"item\"]');\n" +
                    "    if (table) {\n" +
                    "        let currentGF = '';\n" +
                    "        let rows = Array.from(table.querySelectorAll('tr, tbody tr'));\n" +
                    "        for (let row of rows) {\n" +
                    "            let cells = Array.from(row.querySelectorAll('td'));\n" +
                    "            if (cells.length < 2) continue;\n" +
                    "            let gfVal = cells[0].innerText.trim();\n" +
                    "            if (gfVal) currentGF = gfVal;\n" +
                    "            let designation = cells[1].innerText.trim();\n" +
                    "            let codeMatch = designation.match(/^([0-9a-zA-Z]+[_-][0-9a-zA-Z]+)/);\n" +
                    "            if (codeMatch) {\n" +
                    "                let code = codeMatch[1];\n" +
                    "                let name = designation.substring(code.length).replace(/^[^a-zA-Z0-9À-ÿ]+/g, '').trim();\n" +
                    "                if (cells[2]) {\n" +
                    "                    let comment = cells[2].innerText.trim();\n" +
                    "                    if (comment) name += ' (' + comment + ')';\n" +
                    "                }\n" +
                    "                if (currentGF) name = currentGF + ' - ' + name;\n" +
                    "                results.push({ code: code, label: name });\n" +
                    "            }\n" +
                    "        }\n" +
                    "    }\n" +
                    "}\n" +
                    "// 3. Fallback to mainGroupsTable if no right-side subgroups found (VW/Mercedes style)\n" +
                    "if (results.length === 0) {\n" +
                    "    results = Array.from(document.querySelectorAll('[data-test-id=\"mainGroupsTable\"] [data-test-id=\"row\"]')).map(row => {\n" +
                    "        const fgEl = row.querySelector('[data-test-id=\"fgValue\"] span');\n" +
                    "        const idEl = row.querySelector('[data-test-id=\"idValue\"] span');\n" +
                    "        const descrEl = row.querySelector('[data-test-id=\"descrValue\"] span');\n" +
                    "        const descrValEl = row.querySelector('[data-test-id=\"descriptionValue\"] span');\n" +
                    "        return {\n" +
                    "            code: (fgEl ? fgEl.innerText.trim() : '') || (idEl ? idEl.innerText.trim() : ''),\n" +
                    "            label: (descrEl ? descrEl.innerText.trim() : '') || (descrValEl ? descrValEl.innerText.trim() : '')\n" +
                    "        };\n" +
                    "    }).filter(x => x.label);\n" +
                    "}\n" +
                    "return results;";

                List<Map<String, String>> jsResults = (List<Map<String, String>>) driver.executeScript(jsCode);

                if (jsResults != null) {
                    for (Map<String, String> map : jsResults) {
                        String code = map.get("code") != null ? map.get("code").trim() : "";
                        String label = map.get("label") != null ? map.get("label").trim() : "";
                        if (!StringUtils.hasText(label)) {
                            continue;
                        }

                        // Safeguard: do not allow main group codes to be returned as subgroups
                        if (initialCodes.contains(code.toLowerCase())) {
                            continue;
                        }

                        String name = label;
                        if (isValidSubgroupCode(code, groupCode)) {
                            String trimmedCode = code.trim();
                            if (name.startsWith(trimmedCode)) {
                                name = name.substring(trimmedCode.length())
                                        .replaceAll("^[^\\p{L}\\p{N}]+", "")
                                        .trim();
                                if (!StringUtils.hasText(name)) {
                                    name = label;
                                }
                            }
                        } else {
                            String lower = label.toLowerCase();
                            if (lower.startsWith("afficher ") || lower.startsWith("show ")) {
                                code = "ACTION::" + label;
                                name = label;
                            } else {
                                continue; // skip headers/footers
                            }
                        }

                        final String finalCode = code.trim();
                        final String finalName = name.trim();
                        boolean already = subgroups.stream().anyMatch(sg -> sg.code().equalsIgnoreCase(finalCode));
                        if (!already) {
                            subgroups.add(new ScrapedSubgroup(finalCode, finalName));
                        }
                    }
                }
            } catch (Exception jsEx) {
                log.error("[Scraper] JS subgroup extraction failed: {}", jsEx.getMessage());
            }

            if (subgroups.isEmpty()) {
                dumpDebug(driver, vin + "_group_" + groupCode, "subgroups_empty");
                log.warn("[Scraper] No subgroup parsed for groupCode={} (rows={})", groupCode, subgroups.size());
            }

            return subgroups;
        } catch (Exception e) {
            log.error("[Scraper] Failed to fetch subgroups: {}", e.getMessage(), e);
            throw new IllegalStateException("Erreur lors de la recuperation des sous-groupes: " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the parts list and the schematic image for a selected subgroup.
     */
    public ScrapedSubgroupDetails fetchPartsAndSchematic(RemoteWebDriver driver, String vin, String subgroupCode) {
        log.info("[Scraper] Fetching details (parts + schematic) for subgroupCode={} (VIN={})", subgroupCode, vin);
        if (driver == null) {
            throw new IllegalStateException("WebDriver non initialisé.");
        }
        try {
            driver.switchTo().defaultContent();

            if (StringUtils.hasText(subgroupCode) && subgroupCode.startsWith("ACTION::")) {
                String actionLabel = subgroupCode.substring("ACTION::".length()).trim();
                WebElement actionRow = findElementByTextOrAttr(driver, actionLabel,
                        By.cssSelector("[data-test-id=\"mainGroupsTable\"] [data-test-id=\"row\"]"),
                        By.cssSelector("[data-test-id=\"mainGroupsTable\"] [data-test-id=\"descriptionValue\"]"),
                        By.cssSelector("[data-test-id=\"mainGroupsTable\"] [data-test-id=\"descrValue\"]")
                );
                if (actionRow == null) {
                    dumpDebug(driver, vin + "_action_" + actionLabel, "action_subgroup_not_found");
                    throw new IllegalStateException("Action sous-groupe introuvable: " + actionLabel);
                }
                safeClick(driver, actionRow);
                sleep(1500);
            } else {
                WebElement subgroupElement = findElementByTextOrAttr(driver, subgroupCode,
                        By.cssSelector("[data-test-id=\"subGroupsTable\"] [data-test-id=\"row\"]"),
                        By.cssSelector(".subgroup-item"),
                        By.cssSelector(".subgroups-list li"),
                        By.cssSelector("table.subgroups tr"),
                        By.cssSelector("a.subgroup-link"),
                        By.cssSelector("[data-test-id=\"mainGroupsTable\"] [data-test-id=\"row\"]"),
                        By.cssSelector("table#parts tr"),
                        By.cssSelector("table.parts tr"),
                        By.cssSelector("tr.part-row"),
                        By.cssSelector("tr.item"),
                        By.cssSelector("table tr")
                );

                if (subgroupElement == null) {
                    dumpDebug(driver, vin + "_subgroup_" + subgroupCode, "subgroup_not_found");
                    throw new IllegalStateException("Sous-groupe '" + subgroupCode + "' introuvable.");
                }

                safeClick(driver, subgroupElement);
                sleep(1500);
            }

            String imagePath = downloadSchematicImage(driver, subgroupCode);
            List<ScrapedPart> parts = extractPartsTable(driver);

            return new ScrapedSubgroupDetails(imagePath, parts);
        } catch (Exception e) {
            log.error("[Scraper] Failed to fetch subgroup details: {}", e.getMessage(), e);
            throw new IllegalStateException("Erreur lors de la recuperation des pieces et schemas: " + e.getMessage(), e);
        }
    }

    // --- Helper Methods ---

    private boolean isValidSubgroupCode(String code, String groupCode) {
        if (!StringUtils.hasText(code)) {
            return false;
        }
        String trimmedCode = code.trim();
        String trimmedGroup = groupCode != null ? groupCode.trim() : "";
        if (trimmedCode.equalsIgnoreCase(trimmedGroup)) {
            return false;
        }
        return trimmedCode.matches(".*[0-9a-zA-Z].*");
    }

    private List<ScrapedGroup> extractMainGroups(WebDriver driver) {
        if (driver instanceof RemoteWebDriver) {
            try {
                List<Map<String, String>> results = (List<Map<String, String>>) ((RemoteWebDriver) driver).executeScript(
                    "return Array.from(document.querySelectorAll('[data-test-id=\"mainGroupsTable\"] [data-test-id=\"row\"]')).map(row => {" +
                    "    const codeEl = row.querySelector('[data-test-id=\"idValue\"] span');" +
                    "    const nameEl = row.querySelector('[data-test-id=\"descriptionValue\"] span');" +
                    "    return {" +
                    "        code: codeEl ? codeEl.innerText.trim() : ''," +
                    "        name: nameEl ? nameEl.innerText.trim() : ''" +
                    "    };" +
                    "}).filter(x => x.code && x.name);"
                );
                List<ScrapedGroup> groups = new ArrayList<>();
                if (results != null) {
                    for (Map<String, String> map : results) {
                        groups.add(new ScrapedGroup(map.get("code").trim(), map.get("name").trim()));
                    }
                }
                if (!groups.isEmpty()) {
                    return groups;
                }
            } catch (Exception ex) {
                log.error("[Scraper] Failed to extract groups using JS: {}", ex.getMessage());
            }
        }

        List<ScrapedGroup> groups = new ArrayList<>();
        List<WebElement> rows = findElementsInAnyContext(driver, By.cssSelector("[data-test-id=\"mainGroupsTable\"] [data-test-id=\"row\"]"));

        for (WebElement row : rows) {
            try {
                String code = textOf(row, By.cssSelector("[data-test-id=\"idValue\"] span"));
                String name = textOf(row, By.cssSelector("[data-test-id=\"descriptionValue\"] span"));

                if (!StringUtils.hasText(code) || !StringUtils.hasText(name)) {
                    continue;
                }

                boolean already = groups.stream().anyMatch(g -> g.code().equalsIgnoreCase(code));
                if (!already) {
                    groups.add(new ScrapedGroup(code.trim(), name.trim()));
                }
            } catch (Exception ex) {
                log.debug("[Scraper] Skip main group row: {}", ex.getMessage());
            }
        }
        return groups;
    }

    private String downloadSchematicImage(WebDriver driver, String subgroupCode) {
        try {
            log.info("[Scraper] Attempting to download schematic image for subgroupCode={}", subgroupCode);
            
            // Ensure target directory exists
            Path uploadDir = Paths.get(properties.getImageStoragePath());
            Files.createDirectories(uploadDir);

            // Find image element
            WebElement imgElement = findElementWithCandidates(driver, "Illustration Image",
                    By.cssSelector("imageserver-weco"),
                    By.cssSelector("img.illustration"),
                    By.cssSelector("#illustration img"),
                    By.cssSelector("img[src*='illustration']"),
                    By.cssSelector("img[src*='image']"),
                    By.cssSelector("svg") // In case it's inline SVG
            );

            if (imgElement == null) {
                log.warn("[Scraper] No illustration image element found on the page.");
                return null;
            }

            String tagName = imgElement.getTagName().toLowerCase();
            String fileName = subgroupCode + ( "svg".equals(tagName) ? ".svg" : ".png" );
            Path targetPath = uploadDir.resolve(fileName);

            if ("svg".equals(tagName)) {
                // If it is SVG, save the outerHTML as file
                String svgContent = imgElement.getAttribute("outerHTML");
                Files.writeString(targetPath, svgContent);
                log.info("[Scraper] SVG schematic saved to {}", targetPath.toAbsolutePath());
            } else {
                // If it is regular image or custom component, use JS Canvas draw
                if (driver instanceof JavascriptExecutor) {
                    String base64Image = (String) ((JavascriptExecutor) driver).executeScript(
                            "const img = arguments[0];" +
                                    "if (!img) return null;" +
                                    "if (img.tagName.toLowerCase() === 'imageserver-weco') {" +
                                    "    const canvas = img.shadowRoot ? img.shadowRoot.querySelector('canvas.draw-target') : null;" +
                                    "    return canvas ? canvas.toDataURL('image/png') : null;" +
                                    "}" +
                                    "if (img.naturalWidth === 0) return null;" +
                                    "const canvas = document.createElement('canvas');" +
                                    "canvas.width = img.naturalWidth;" +
                                    "canvas.height = img.naturalHeight;" +
                                    "const ctx = canvas.getContext('2d');" +
                                    "ctx.drawImage(img, 0, 0);" +
                                    "return canvas.toDataURL('image/png');", imgElement
                    );

                    if (base64Image == null || !base64Image.startsWith("data:image/png;base64,")) {
                        log.warn("[Scraper] JS Canvas failed to extract base64 from image. Trying Selenium screenshot...");
                        byte[] bytes = imgElement.getScreenshotAs(OutputType.BYTES);
                        Files.write(targetPath, bytes);
                    } else {
                        String cleanBase64 = base64Image.substring("data:image/png;base64,".length());
                        byte[] bytes = Base64.getDecoder().decode(cleanBase64);
                        Files.write(targetPath, bytes);
                    }
                } else {
                    byte[] bytes = imgElement.getScreenshotAs(OutputType.BYTES);
                    Files.write(targetPath, bytes);
                }
                log.info("[Scraper] PNG schematic saved to {}", targetPath.toAbsolutePath());
            }

            // Return relative path for frontend access
            return "/uploads/partslink/" + fileName;

        } catch (Exception ex) {
            log.error("[Scraper] Failed to download schematic image: {}", ex.getMessage(), ex);
            return null;
        }
    }

    private List<ScrapedPart> extractPartsTable(WebDriver driver) {
        if (driver instanceof RemoteWebDriver) {
            try {
                List<Map<String, String>> results = (List<Map<String, String>>) ((RemoteWebDriver) driver).executeScript(
                    "let container = document.querySelector('[data-test-id=\"bomlistPartList\"]');" +
                    "if (container) {" +
                    "    let rows = Array.from(container.querySelectorAll('[data-test-id=\"row\"]'));" +
                    "    return rows.map(row => {" +
                    "        let posEl = row.querySelector('[data-test-id=\"posValue\"]');" +
                    "        let partnoEl = row.querySelector('[data-test-id=\"partnoValue\"]');" +
                    "        let descrEl = row.querySelector('[data-test-id=\"descriptionValue\"]');" +
                    "        let remarkEl = row.querySelector('[data-test-id=\"remarkValue\"]');" +
                    "        let qtyEl = row.querySelector('[data-test-id=\"qtyValue\"]');" +
                    "        let aeEl = row.querySelector('[data-test-id=\"aeValue\"]');" +
                    "        return {" +
                    "            position: posEl ? posEl.innerText.trim() : ''," +
                    "            partNumber: partnoEl ? partnoEl.innerText.trim() : ''," +
                    "            designation: descrEl ? descrEl.innerText.trim() : ''," +
                    "            infoSuppl: remarkEl ? remarkEl.innerText.trim() : ''," +
                    "            quantity: qtyEl ? qtyEl.innerText.trim() : '1'," +
                    "            ae: aeEl ? aeEl.innerText.trim() : ''" +
                    "        };" +
                    "    }).filter(x => x.partNumber || x.position);" +
                    "}" +
                    "const table = document.querySelector('table#parts, table.parts-list, table.parts, .parts-table table, table[class*=\"part\"], table[class*=\"item\"]');" +
                    "if (!table) return [];" +
                    "return Array.from(table.querySelectorAll('tr.part-row, tr.item, tbody tr')).map(row => {" +
                    "    const cells = Array.from(row.querySelectorAll('td'));" +
                    "    if (cells.length < 3) return null;" +
                    "    return {" +
                    "        position: cells[0].innerText.trim()," +
                    "        partNumber: cells[1] ? cells[1].innerText.trim() : ''," +
                    "        designation: cells[2] ? cells[2].innerText.trim() : ''," +
                    "        infoSuppl: cells[3] ? cells[3].innerText.trim() : ''," +
                    "        quantity: cells[4] ? cells[4].innerText.trim() : '1'," +
                    "        ae: cells[5] ? cells[5].innerText.trim() : ''" +
                    "    };" +
                    "}).filter(x => x !== null && (x.partNumber || x.position));"
                );
                List<ScrapedPart> parts = new ArrayList<>();
                if (results != null) {
                    for (Map<String, String> map : results) {
                        parts.add(new ScrapedPart(
                                map.get("position"),
                                map.get("partNumber"),
                                map.get("designation"),
                                map.get("infoSuppl"),
                                map.get("quantity"),
                                map.get("ae")
                        ));
                    }
                }
                if (!parts.isEmpty()) {
                    return parts;
                }
            } catch (Exception ex) {
                log.error("[Scraper] Failed to extract parts using JS: {}", ex.getMessage());
            }
        }

        List<ScrapedPart> parts = new ArrayList<>();
        WebElement tableElement = findElementWithCandidates(driver, "Parts Table",
                By.cssSelector("table#parts"),
                By.cssSelector("table.parts-list"),
                By.cssSelector("table.parts"),
                By.cssSelector(".parts-table table"),
                By.xpath("//table[contains(@class,'part') or contains(@class,'item')]")
        );

        if (tableElement == null) {
            log.warn("[Scraper] Parts table not found on page.");
            return parts;
        }

        List<WebElement> rows = tableElement.findElements(By.cssSelector("tr.part-row, tr.item, tbody tr"));
        for (WebElement row : rows) {
            try {
                List<WebElement> cells = row.findElements(By.cssSelector("td"));
                if (cells.size() < 3) continue; // Skip header/footer rows

                String pos = cells.get(0).getText().trim();
                
                // Partslink columns: Pos, Part Number, Designation, Info Suppl, Quantity, AE
                String partNo = "";
                String designation = "";
                String infoSuppl = "";
                String qty = "1";
                String ae = "";

                if (cells.size() >= 2) partNo = cells.get(1).getText().trim();
                if (cells.size() >= 3) designation = cells.get(2).getText().trim();
                if (cells.size() >= 4) infoSuppl = cells.get(3).getText().trim();
                if (cells.size() >= 5) qty = cells.get(4).getText().trim();
                if (cells.size() >= 6) ae = cells.get(5).getText().trim();

                // If part number looks like header designation, clean up or skip
                if (!StringUtils.hasText(partNo) && !StringUtils.hasText(pos)) {
                    continue;
                }

                parts.add(new ScrapedPart(pos, partNo, designation, infoSuppl, qty, ae));
            } catch (Exception ex) {
                log.debug("[Scraper] Skipped parsing row: {}", ex.getMessage());
            }
        }
        return parts;
    }

    private WebElement findElementWithCandidates(WebDriver driver, String name, By... candidates) {
        // Wait up to 5 seconds (5 poll attempts) for at least one candidate to be present and displayed in any frame context
        for (int i = 0; i < 5; i++) {
            for (By by : candidates) {
                try {
                    List<WebElement> elements = findElementsInAnyContext(driver, by);
                    if (!elements.isEmpty() && elements.get(0).isDisplayed()) {
                        log.debug("[Scraper] Found element '{}' using: {}", name, by);
                        return elements.get(0);
                    }
                } catch (Exception ignored) {
                }
            }
            sleep(1000);
        }
        return null;
    }

    private WebElement findElementByTextOrAttr(WebDriver driver, String keyword, By... candidates) {
        for (int i = 0; i < 3; i++) {
            for (By by : candidates) {
                try {
                    List<WebElement> elements = findElementsInAnyContext(driver, by);
                    for (WebElement el : elements) {
                        String text = el.getText().trim();
                        String id = el.getAttribute("id");
                        String href = el.getAttribute("href");
                        String classAttr = el.getAttribute("class");
                        
                        boolean match = text.toLowerCase().contains(keyword.toLowerCase())
                                || (id != null && id.toLowerCase().contains(keyword.toLowerCase()))
                                || (href != null && href.toLowerCase().contains(keyword.toLowerCase()))
                                || (classAttr != null && classAttr.toLowerCase().contains(keyword.toLowerCase()));

                        if (match) {
                            return el;
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            sleep(1000);
        }
        return null;
    }

    private WebElement findGroupRowByCode(List<WebElement> rows, String groupCode) {
        if (rows == null || rows.isEmpty() || !StringUtils.hasText(groupCode)) {
            return null;
        }
        for (WebElement row : rows) {
            try {
                String code = textOf(row, By.cssSelector("[data-test-id=\"idValue\"] span"));
                if (!StringUtils.hasText(code)) {
                    code = textOf(row, By.cssSelector("[data-test-id=\"fgValue\"] span"));
                }
                if (StringUtils.hasText(code) && code.trim().equalsIgnoreCase(groupCode.trim())) {
                    return row;
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String extractBrandFromCurrentUrl(String url) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        Matcher m = Pattern.compile("/pl24-app/([^/]+)/").matcher(url);
        if (m.find()) {
            return m.group(1);
        }
        return null;
    }

    private String buildVehicleUrl(String vin, String brandCode, String currentUrl) {
        String resolvedBrand = StringUtils.hasText(brandCode) ? brandCode : extractBrandFromCurrentUrl(currentUrl);
        
        // Fallback: Infer brand from VIN if possible
        if (!StringUtils.hasText(resolvedBrand)) {
            if (vin.toUpperCase().startsWith("WBA")) resolvedBrand = "bmw";
            else if (vin.toUpperCase().startsWith("WDD")) resolvedBrand = "mercedes-benz";
            else if (vin.toUpperCase().startsWith("WVW")) resolvedBrand = "volkswagen";
        }

        if (StringUtils.hasText(resolvedBrand)) {
            return "https://www.partslink24.com/pl24-app/" + resolvedBrand + "/" + vin + "/0/vehicle?desktop=true&lang=fr";
        }
        return null;
    }

    private String parseFieldByText(String panelText, String fieldPattern, String fallback) {
        if (!StringUtils.hasText(panelText)) return fallback;

        Pattern pattern = Pattern.compile("(?i)(" + fieldPattern + ")\\s*[:\\-]?\\s*([^\\r\\n]+)");
        Matcher matcher = pattern.matcher(panelText);
        if (matcher.find()) {
            return matcher.group(2).trim();
        }

        return fallback;
    }

    private String textOf(WebElement parent, By selector) {
        try {
            WebElement el = parent.findElement(selector);
            return el != null ? el.getText().trim() : "";
        } catch (Exception ignored) {
            return "";
        }
    }

    private void safeClick(RemoteWebDriver driver, WebElement element) {
        if (element == null) return;
        try {
            new WebDriverWait(driver, Duration.ofSeconds(8))
                    .until(ExpectedConditions.elementToBeClickable(element))
                    .click();
            return;
        } catch (Exception ignored) {
        }
        try {
            ((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:\"center\"});", element);
            sleep(150);
            ((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
            return;
        } catch (Exception ignored) {
        }
        element.click();
    }

    private String extractCodeFromText(String text) {
        if (text == null) return "";
        // Extract leading numbers, underscores or hyphens (ex: "34_3137", "34")
        Pattern pattern = Pattern.compile("^([0-9a-zA-Z_-]+)");
        Matcher matcher = pattern.matcher(text.trim());
        if (matcher.find()) {
            return matcher.group(1);
        }
        return "";
    }

    private void dumpDebug(RemoteWebDriver driver, String debugId, String reason) {
        try {
            log.warn("[Scraper] Warning/Error log dump for ID={}, reason={}", debugId, reason);
        } catch (Exception ex) {
            log.error("[Scraper] Failed to write debug: {}", ex.getMessage());
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private List<WebElement> findElementsInAnyContext(WebDriver driver, By selector) {
        // First try default content
        try {
            driver.switchTo().defaultContent();
            List<WebElement> elements = driver.findElements(selector);
            if (!elements.isEmpty()) {
                return elements;
            }
        } catch (Exception ignored) {
        }
        
        // Scan child frames
        List<Integer> framePath = new ArrayList<>();
        return findElementsInChildFrame(driver, selector, framePath, 0, 3);
    }

    private List<WebElement> findElementsInChildFrame(WebDriver driver, By selector, List<Integer> currentPath, int depth, int maxDepth) {
        if (depth >= maxDepth) {
            return new ArrayList<>();
        }
        
        try {
            switchToFramePath(driver, currentPath);
            List<WebElement> elements = driver.findElements(selector);
            if (!elements.isEmpty()) {
                return elements;
            }
        } catch (Exception ignored) {
            try {
                driver.switchTo().defaultContent();
            } catch (Exception ignored2) {}
        }
        
        int frameCount = 0;
        try {
            switchToFramePath(driver, currentPath);
            frameCount = driver.findElements(By.cssSelector("iframe, frame")).size();
        } catch (Exception ignored) {
            return new ArrayList<>();
        }

        for (int i = 0; i < frameCount; i++) {
            List<Integer> childPath = new ArrayList<>(currentPath);
            childPath.add(i);
            try {
                List<WebElement> found = findElementsInChildFrame(driver, selector, childPath, depth + 1, maxDepth);
                if (!found.isEmpty()) {
                    return found;
                }
            } catch (Exception ignored) {
                try {
                    driver.switchTo().defaultContent();
                } catch (Exception ignored2) {}
            }
        }
        
        try {
            driver.switchTo().defaultContent();
        } catch (Exception ignored) {}
        return new ArrayList<>();
    }

    private void switchToFramePath(WebDriver driver, List<Integer> framePath) {
        driver.switchTo().defaultContent();
        for (Integer frameIndex : framePath) {
            List<WebElement> frames = driver.findElements(By.cssSelector("iframe, frame"));
            if (frameIndex >= 0 && frameIndex < frames.size()) {
                driver.switchTo().frame(frames.get(frameIndex));
            }
        }
    }
}
