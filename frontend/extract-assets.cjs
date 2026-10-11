const sharp = require('sharp');
const path = require('path');

const src = 'C:/Users/PANKAJ SINGH/.gemini/antigravity/brain/55cf8ada-4db3-4c6d-8b6d-2e55bea93782/.user_uploaded/media_1791610494772_6dfd07c3.png';
const outDir = path.join(__dirname, 'public/images/fintrack');

// Total dimensions: 735 x 490
// Let's define the bounding boxes of key elements:
const assets = [
  // 1. Character with desk and decor (lower right area: x ~ 430 to 735, y ~ 180 to 490)
  { name: 'character-full', left: 430, top: 180, width: 305, height: 310 },
  // Character head/body zoomed
  { name: 'character-working', left: 520, top: 180, width: 215, height: 295 },
  // Avatar in top right
  { name: 'avatar-user', left: 668, top: 18, width: 36, height: 36 },
  // Wallet illustration (Summary card 1)
  { name: 'wallet-coin', left: 220, top: 96, width: 50, height: 58 },
  // Money bag illustration (Summary card 2)
  { name: 'money-bag', left: 362, top: 94, width: 46, height: 60 },
  // Shopping bag illustration (Summary card 3)
  { name: 'shopping-bag', left: 497, top: 97, width: 45, height: 57 },
  // Golden coins stack (Summary card 4)
  { name: 'coins-stack', left: 631, top: 111, width: 45, height: 44 },
  // Piggy bank promo card (bottom sidebar)
  { name: 'piggy-bank', left: 74, top: 380, width: 52, height: 54 },
  // Vacation Bali (Goal 1)
  { name: 'vacation-bali', left: 147, top: 405, width: 33, height: 38 },
  // Laptop (Goal 2)
  { name: 'laptop-goal', left: 288, top: 406, width: 33, height: 36 },
  // Lightbulb Smart Tip
  { name: 'smart-tip-bulb', left: 443, top: 421, width: 28, height: 38 },
  // Potted plant Smart Tip
  { name: 'potted-plant-small', left: 625, top: 430, width: 36, height: 42 },
  // Plant on shelf top right
  { name: 'plant-shelf', left: 686, top: 76, width: 42, height: 75 },
  // Frame scenery on wall
  { name: 'scenery-frame', left: 665, top: 175, width: 62, height: 78 },
  // Transaction icons
  { name: 'tx-starbucks', left: 390, top: 213, width: 24, height: 24 },
  { name: 'tx-uber', left: 390, top: 250, width: 24, height: 24 },
  { name: 'tx-salary', left: 390, top: 282, width: 24, height: 24 },
  { name: 'tx-amazon', left: 390, top: 318, width: 24, height: 24 },
];

async function extract() {
  for (const item of assets) {
    await sharp(src)
      .extract({ left: item.left, top: item.top, width: item.width, height: item.height })
      .png()
      .toFile(path.join(outDir, `${item.name}.png`));
    console.log(`Extracted: ${item.name}`);
  }
}

extract().catch(console.error);
