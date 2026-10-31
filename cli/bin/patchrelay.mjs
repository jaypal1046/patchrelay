#!/usr/bin/env node
import { main } from '../src/main.mjs';

main().catch(error => {
  console.error(`PatchRelay: ${error.message}`);
  process.exitCode = 1;
});
