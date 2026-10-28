# Year-to-Date Attendance Analytics & Due Fine Report

Upgrade the **Summary** tab into a comprehensive **Year-to-Date Analytical Dashboard & Due Fine Sheet** that lets teachers inspect total school days, month-by-month absence breakdowns, present/absent totals, automatically calculated due fines (৳), and detailed student attendance logs up to any selected date in the academic year—complete with one-tap CSV and PDF exports matching the Class 9(B) spreadsheet format.

### User Review & Critical Decisions

> [!IMPORTANT]
> Please review the confirmed design choices below and click **Proceed** when you are ready to build.

- **Confirmed Decision 1 — Navigation & Placement**: Upgrade the existing **Summary** tab with a segmented view switcher (**Overview Cards** vs. **Monthly Fine Sheet**) so all analytics, monthly breakdowns, due fines, and student logs live in one unified place without crowding the bottom navigation bar.
- **Confirmed Decision 2 — School Days Calculation**: Count a day as an active "School Day" in a given month only when attendance was actually recorded in the app on that date (excluding unmarked calendar days, weekends, and holidays), up to and including the selected "Till Date" cutoff.
- **Confirmed Decision 3 — Report Exports**: Provide both **CSV** (UTF-8 with BOM for Excel compatibility, matching the exact layout of your Class 9(B) sheet with header metadata, per-month school days row, per-student monthly absent counts, total present/absent, due fine, and bottom `TOTAL` row) and **PDF** report exports directly from the Summary header.
- **Default Fine Rate Persistence**: The configurable **Fine per Absent Day (৳)** defaults to `5` and is saved in app preferences so changing it immediately recalculates every student's due fine and persists across app restarts.

---

### 1. Overview & Core Concept

- **What It Does**:
  - Lets teachers pick any **"Till Date" cutoff** within the active academic year (defaulting to today) to inspect cumulative attendance analytics up to that exact day.
  - Provides an interactive **Fine per Absent Day (৳)** control (e.g., `৳5`) that instantaneously recalculates each student's individual due fine and the class-wide total due fine.
  - Displays the **School Days Counted** for each active month alongside the year-to-date total school days.
  - Offers two complementary views inside the Summary tab:
    1. **Student Analytics Cards**: Rich per-student cards showing Present days, Absent days, Attendance %, Due Fine (৳), Consecutive Absence streak alerts, and visual progress bars.
    2. **Monthly Breakdown & Fine Table**: A sticky-column horizontal matrix matching the uploaded spreadsheet (`Roll | Name | Month 1 | Month 2 | ... | Total Present | Total Absent | Due Fine (৳) | Status`) with a pinned **School Days Counted** row at the top and a **Class Total** summary footer at the bottom.
  - Opens a **Student Analytical Detail & Absent Log Modal** when any student row or card is tapped, showing their month-by-month present/absent breakdown, due fine summary, and a filterable date-by-date log (**All Dates**, **Absent Only**, **Present Only**) with quick status toggling.
- **Target Audience / Persona**: Classroom teachers managing daily attendance registers, tracking chronic absenteeism, collecting absence fines, and preparing official analytical reports for school administration.
- **Key Value**: Eliminates manual spreadsheet tallying by generating real-time month-by-month absence counts, school-day totals, due fine calculations, and ready-to-print CSV/PDF sheets directly from recorded attendance.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Selecting Cutoff Date & Fine Rate**: At the top of the Summary screen, a compact control bar displays the active **"Till Date"** pill (tap to open a DatePicker to view analytics as of any past or current date in the year) and the **"Fine / Absent (৳)"** chip (tap to edit the per-day fine amount, e.g., `5`).
  2. **Reviewing Class-Wide KPIs**: Four KPI cards summarize the period up to the cutoff date: **School Days Counted**, **Class Attendance %**, **Total Absent & Due Fine (৳)**, and **2+ Consecutive Absence Alerts**.
  3. **Switching Between Cards & Monthly Fine Table**: A segmented toggle switches smoothly between:
     - **Cards View**: Searchable, sortable student cards with sort chips for *Roll*, *Lowest %*, *Highest Fine / Absent*, and *2+ Consecutive Absences*.
     - **Monthly Fine Sheet View**: A structured table with a sticky left `Roll & Name` column, scrollable active month columns (showing both the month's school days counted and each student's absent count in that month), `Total Present`, `Total Absent`, `Due Fine (৳)`, and enrollment `Status`, plus a pinned `TOTAL` row summing monthly absences and total class fine due.
  4. **Inspecting a Student's Absent Log**: Tapping any student in either view opens the **Student Analytical Profile Modal**, featuring:
     - Top summary badges: *Present / School Days*, *Absent Days*, *Attendance %*, and *Due Fine (৳)*.
     - A compact *Month-by-Month Chip Strip* showing absences per month (e.g., `Apr: 2/17`, `May: 1/15`).
     - A filterable *Attendance & Absent Log* (**Absent Log Only** vs. **All Recorded Days**) listing exact dates and days of the week, with a one-tap toggle if a record needs correction.
  5. **Exporting the Analytical Sheet**: Tapping the **Export** button in the Summary header offers **Export Analytical Fine Sheet (CSV)** and **Export Analytical Fine Sheet (PDF)**, generating files formatted like the Class 9(B) reference sheet up to the selected cutoff date.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Clean editorial data-table clarity paired with Material Design 3 adaptive surfaces.
  - *Color Palette & Mood*: Fully adaptive across Light, Dark, Forest, and Ocean themes using luminance-aware semantic tokens:
    - Present metrics & badges: Emerald (`#2E7D32` light / `#81C784` dark)
    - Absent & Due Fine highlights: Crimson (`#C62828` light / `#FF8A80` dark)
    - Fine & School-Day summary containers: Warm Amber / Primary Tonal surfaces
  - *Typography & Hierarchy*: Bold tabular numerals for monthly counts, totals, and currency (`৳`), with clear secondary labels for ratios and dates.
  - *Component Styling & Layout*: Sticky left column (`Roll / Name`) synchronized with horizontal scroll for monthly columns, elevated KPI summary cards, and filter chips with 48dp minimum touch targets.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Unified Summary Tab with Segmented Mode Switcher (`Cards` vs. `Monthly Sheet`)**
  - *Chosen Approach*: Enhance the existing Summary screen with a top segmented toggle between **Student Cards** and **Monthly Fine Table**, sharing the same top filter bar, cutoff date picker, fine-per-day setting, and KPI header.
  - *Why*: Keeps all analytical tools in one intuitive destination while supporting both quick visual scanning (Cards) and spreadsheet-style cross-month comparison (Monthly Fine Table).
  - *Alternatives Considered*: Adding a 6th bottom navigation tab, which would overcrowd the mobile navigation bar and split related analytics across two screens.
- **Decision 2: Empirical "School Days Counted" Derived from Recorded Attendance**
  - *Chosen Approach*: A date (`<= cutoffDate`, not a weekend, and not a custom holiday) is counted as a school day if at least one student has a recorded attendance entry (`P` or `A`) on that date.
  - *Why*: Matches how real classroom registers work—months where school was closed or before attendance tracking started naturally show exact recorded session counts (e.g., `Apr: 17, May: 15, ... Total: 103`) without inflating totals with unrecorded calendar days.
- **Decision 3: Persistent Fine Per Absent Day Setting**
  - *Chosen Approach*: Store `finePerAbsentDay` (integer/float, default `5`) in local DataStore preferences so teachers can change it anytime with a single tap and have it persist across sessions.
  - *Why*: Lightweight, instant reactive updates via Kotlin `StateFlow`, and zero schema migration risk.

---

### 4. Technical Architecture & Data Strategy *(Technical Reference)*

- **Architecture & Component Diagram**:

```
┌───────────────────────────────────────────────────────────────────────────┐
│                         Data & Persistence Layer                          │
│  ┌─────────────────────────┐              ┌────────────────────────────┐  │
│  │ Room Database (SQLite)  │              │ DataStore Preferences      │  │
│  │ • students              │              │ • academic_year            │  │
│  │ • attendance_records    │              │ • custom_holidays          │  │
│  └───────────┬─────────────┘              │ • fine_per_absent_day (৳)  │  │
│              └─────────────┬──────────────┴──────────────┬─────────────┘  │
└────────────────────────────┼─────────────────────────────┼────────────────┘
                             ▼                             ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                     ClassRollViewModel (StateFlow)                        │
│  • allAttendanceForYear, students, holidays, finePerAbsentDay             │
│  • updateFinePerAbsentDay(amount)                                         │
└────────────────────────────────────┬──────────────────────────────────────┘
                                     ▼
┌───────────────────────────────────────────────────────────────────────────┐
│                 SummaryDashboardScreen (Till-Date Engine)                 │
│  ┌─────────────────────────────────────────────────────────────────────┐  │
│  │ Top Control Bar: Cutoff Date Picker ("Till Date") | Fine Rate (৳)   │  │
│  │                  | Export Menu (CSV & PDF Analytical Sheet)         │  │
│  ├─────────────────────────────────────────────────────────────────────┤  │
│  │ KPI Strip: School Days | Class Avg % | Total Fine (৳) | 2+ Alerts   │  │
│  ├─────────────────────────────────────────────────────────────────────┤  │
│  │ View Mode Switcher: [ Student Cards ]  <--->  [ Monthly Fine Sheet ]│  │
│  │  • Search & Sort Chips (Roll, Name, Lowest %, Highest Fine, Streak) │  │
│  │  • Monthly Fine Table:                                              │  │
│  │    - Pinned "School days counted" row per active month & YTD total  │  │
│  │    - Per-student monthly absent cells, Total P, Total A, Fine (৳)   │  │
│  │    - Pinned "TOTAL" footer row summing monthly absences & fines     │  │
│  ├─────────────────────────────────────────────────────────────────────┤  │
│  │ Student Analytical Detail & Absent Log Modal (on Student Tap)       │  │
│  │  • YTD Present, Absent, Attendance %, Due Fine (৳), Monthly Chips   │  │
│  │  • Filterable Log: [ Absent Log Only ] | [ All Recorded Dates ]     │  │
│  └─────────────────────────────────────────────────────────────────────┘  │
└───────────────────────────────────────────────────────────────────────────┘
```

- **Data Model & Analytical Computation**:
  - **Cutoff Filtering**: Filter `allAttendanceForYear` to records where `record.date <= selectedCutoffDate` and `record.date !in holidays` and `!isWeekend(record.date)`.
  - **Active Months & School Days Counted**:
    - Group valid recorded dates (`distinct()`) by `yyyy-MM` sorted chronologically.
    - For each active month `m`, `schoolDaysByMonth[m]` = count of distinct recorded dates in month `m`; `totalSchoolDays` = sum of `schoolDaysByMonth.values`.
  - **Per-Student Analytical Model**:
    - `monthlyAbsences: Map<String, Int>`: count of `"A"` records in each active month `m`.
    - `totalPresent`: count of `"P"` records up to `selectedCutoffDate`.
    - `totalAbsent`: count of `"A"` records up to `selectedCutoffDate`.
    - `dueFine`: `totalAbsent * finePerAbsentDay`.
    - `statusLabel`: `"Active"` if the student has recorded attendance up to the latest active month, `"No records"` if `totalPresent + totalAbsent == 0`, or `"Ends <Last Date>"` if their last recorded entry ended in an earlier month while the class continued.
- **Interactive Component & State Mapping**:
  - **Cutoff Date Picker**: Selecting any date updates `selectedCutoffDate` state, immediately recomputing active months, school days counted, student monthly breakdowns, and totals.
  - **Fine Rate Dialog**: Tapping the `Fine: ৳5/day` chip opens a compact numeric input dialog calling `viewModel.updateFinePerAbsentDay(newAmount)`, immediately updating all student and class fine totals.
  - **Student Detail & Absent Log Modal**: Tapping a student card or table row opens their modal with a segmented filter (`Absent Log (${absentCount})` vs. `All History (${totalClasses})`) and an inline `Toggle P/A` action that updates Room and Firestore reactively.
  - **CSV & PDF Analytical Exporters**:
    - **CSV Exporter**: Writes UTF-8 BOM + title row (`Academic Year — Absent days & due fine (till <Date>)`), fine rate row, `School days counted` row per month + total, column headers (`Roll,Name,<Months...>,Total Present,Total Absent,Due Fine (৳),Status`), student rows, and the bottom `TOTAL` row.
    - **PDF Exporter**: Renders a multi-page landscape/portrait analytical table with title, cutoff date, school days summary, per-month absence columns, total present/absent, due fine (৳), and footer totals.
