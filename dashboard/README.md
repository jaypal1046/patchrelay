# PatchRelay dashboard

Next.js App Router site describing the PatchRelay CLI and release workflow.

## Run locally

Requirements: Node.js 22+.

```sh
npm install
npm run dev
```

Open http://localhost:3000.

## Production

```sh
npm run build
npm start
```

The dashboard is static content; CLI commands run in a developer's Android project and are not invoked by this site.
