# Секреты, подпись, бэкенд

Читать, когда трогаешь сеть, `local.properties`, подпись или схему БД в части
имён `tmdb`.

## Secrets and signing

`local.properties` holds `backend.url` / `backend.token`, the `donate.*` destinations (see
`ai-docs/distribution-and-legal.md`) and the `release.*` signing credentials;
`keystore/dosmotr-release.jks` holds the key. Both are gitignored and exist
only on this machine — losing the keystore means never being able to update a published
build.

With no `backend.url` the app still builds; the search screen shows an explanatory empty
state and only manual entry works. `backend.url` without `backend.token` fails the build
on purpose — it would otherwise 403 on every call at runtime.

**The app knows one remote: its own backend.** Which catalogue that backend reads from is
not represented anywhere in this codebase, and deliberately so — the source can change
server-side without a new build. Concretely: `CatalogApi` speaks the app's own
`/v1/search`, `/v1/tv/{id}`, `/v1/tv/{id}/season/{n}`, `/v1/movie/{id}`; artwork comes
from `/img/{size}/{path}` via `CatalogImage`; the single credential is `@BackendToken`,
sent as `X-Backend-Token`. Language and adult filtering are pinned on the backend, not
here, because they are properties of the source.

Two persisted names still say `tmdb`: the `tmdbId` / `tmdbRating` columns of `titles` and
the `tmdb_id` / `tmdb_rating` keys in backup JSON. Kotlin calls them `catalogId` and
`rating`, mapped with `@ColumnInfo` / `@SerialName`. **Renaming them for real is not a
cosmetic change**: a column rename is a schema change like any other, so it costs a
hand-written entry in `AppDatabase.MIGRATIONS` that copies every existing library across —
and a rename shipped *without* one no longer wipes the library, it fails to open the
database at all. Changing the JSON keys separately breaks importing older backups.

The backend is a **separate project**, not part of this build: `~/projects/dosmotr-backend`
(nginx + Caddy, deployed with Docker Compose). Nothing here depends on it at compile
time — the coupling is the two `local.properties` values, the `X-Backend-Token` header
name, and the `/v1` paths above.
