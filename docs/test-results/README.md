# Dovezi de validare Menene

Acest folder păstrează rezultatele verificărilor obligatorii. Nu se includ fișiere video, APK-uri, chei sau parole.

## Structură recomandată

```text
docs/test-results/
├── YYYY-MM-DD-build.md
├── YYYY-MM-DD-samsung-sm-t290.md
├── YYYY-MM-DD-builder-large-library.md
└── ACCEPTED_RISKS.md
```

## Șablon raport

```markdown
# Test Menene

- Data:
- Commit:
- versionName / versionCode:
- APK SHA-256:
- Dispozitiv:
- Android:
- Bibliotecă:
- Operator:

## Comenzi

## Rezultate

| Test | Rezultat | Dovezi/observații |
|---|---|---|
| G0 | PASS/FAIL | |

## Probleme identificate

## Verdict

GO / NO-GO
```

## Riscuri acceptate

`ACCEPTED_RISKS.md` este folosit numai când un gate nu poate fi trecut, dar proprietarul decide explicit continuarea. Pentru fiecare risc sunt obligatorii:

- identificator;
- motiv;
- impact;
- probabilitate;
- măsură de reducere;
- condiție de remediere;
- aprobarea proprietarului.
