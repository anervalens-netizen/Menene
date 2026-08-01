# Riscuri acceptate

## MENENE-2.4-SOAK-30M

- Release: Menene 2.4.0 / versionCode 8.
- Status: acceptat explicit de proprietar pentru utilizarea personală pe SM-T585.
- Decizie: Andrei, Buzz event `bd59fce2e5ee87c8a5e15f172ec3bfc0ef6650ca036dc0858f2c888480493c65`, 2026-08-01T20:49:32Z.
- Motiv: proprietarul a cerut reducerea probei de două ore „în limitele rezonabilității”, după ce interacțiunea fizică cu tableta a invalidat rularea controlată în curs.
- Abatere: 30 minute de lifecycle/stabilitate cu redări repetate, nu două ore și nu 30 minute continue din același film.
- Impact: un defect rar care apare numai după mai mult de 30 minute de utilizare continuă nu este exclus de această calificare.
- Probabilitate: redusă, dar necunoscută; nu există crash/ANR/playback/OOM în build, testele 3/3 sau probele anterioare.
- Reducere: build complet, 20/20 unit debug, 20/20 unit release, 17/17 connected, redare 3/3, 5 cicluri background/foreground, 3 force-stop/restart, probe de resurse și logcat continuu fail-closed.
- Condiție de remediere: rulează din nou soak-ul standard de două ore înaintea unei distribuții mai largi sau imediat dacă apare crash, ANR, blocare, pierdere audio ori eroare de redare în utilizarea personală.

Această acceptare nu modifică permanent pragul standard din `docs/VALIDATION_PLAN.md`.
