#! /usr/bin/env node

const {writeFileSync} = require('fs');

// These three lines configurable
const n = 100; // number of comms
const m = 10; // size of messages to be sent
const c = 10; // size of continuation to run

// Generate message and continuation strings (ground terms)
const msg = `"${Array(m + 1).join('m')}"`;
const cont = `"${Array(c + 1).join('c')}"`;

// All unique channels
let uniqueChannels = "";
for (let i = 0; i < n; i++) {
  uniqueChannels += `@${i}!(${msg}) | `;
  uniqueChannels += `for(_ <- @${i}){${cont}} |`;
}
uniqueChannels += "Nil";
writeFileSync("uniqueChannels.rho", uniqueChannels);


// Linear sends and listens
let bothLinear = "";
for (let i = 0; i < n; i++) {
  bothLinear += `@1!(${msg}) | `;
  bothLinear += `for(_ <- @1){${cont}} |`;
}
bothLinear += "Nil";
writeFileSync("bothLinear.rho", bothLinear);

// Persistent Send
let persistentSend = "";
for (let i = 0; i < n; i++) {
  persistentSend += `for(_ <- @1){${cont}} |`;
}
persistentSend += `@1!!(${msg})`;
writeFileSync("persistentSend.rho", persistentSend);

// Persistent listen
let persistentListen = "";
for (let i = 0; i < n; i++) {
  persistentListen += `@1!(${msg}) | `;
}
persistentListen += `for(_ <= @1){${cont}}`;
writeFileSync("persistentListen.rho", persistentListen);

// Contract
let contract = "";
for (let i = 0; i < n; i++) {
  contract += `@1!(${msg}) | `;
}
contract += `contract @1(_) = {${cont}}`;
writeFileSync("contract.rho", contract);
