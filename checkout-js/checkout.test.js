// Tests for the checkout auto-fill engine. Uses made-up buyer data only.
// Field names mirror Eventer's own purchase templates (purchaseGuestsForm.html, guestQuestionsDirective.html).
const { test } = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { JSDOM } = require('jsdom');

const SRC = fs.readFileSync(path.join(__dirname, '../app/src/main/assets/checkout.js'), 'utf8');

const PROFILE = {
  fullName: 'דנה לוי', idNumber: '123456782', phone: '0501234567', email: 'dana@example.com',
  gender: 'FEMALE', birthDate: '05-04-1986', age: 40, city: 'חיפה',
  facebookUrl: 'https://facebook.com/dana', instagramUrl: 'https://instagram.com/dana',
  smoker: false, ticketCount: 2,
};

function guestBlock(i) {
  return `
  <div class="guest">
    <input type="text" id="name${i}" name="name" ng-model="guest.name">
    <select id="guestIdSelect${i}" ng-model="guest.isPassportAsId"><option value="b:true">דרכון</option><option value="b:false">תעודת זהות</option></select>
    <input type="tel" name="sid" id="guestId${i}" ng-model="guest.id" required>
    <input type="tel" ng-model="guest.phone" required>
    <input type="email" name="email" ng-model="guest.email" required>
    <select id="gender${i}" name="gender" ng-model="guest.isMale" required><option value="">בחר</option><option value="b:true">זכר</option><option value="b:false">נקבה</option></select>
  </div>`;
}

function load(html, opts = {}) {
  const dom = new JSDOM(`<!doctype html><body>${html}</body>`, { runScripts: 'outside-only', pretendToBeVisual: true });
  const w = dom.window;
  w.__clicks = [];
  w.document.addEventListener('click', (e) => w.__clicks.push(e.target.getAttribute('data-id') || e.target.className || e.target.tagName), true);
  w.eval(SRC);
  w.__ew.start(Object.assign({}, PROFILE, opts.profile || {}), { manual: true });
  return w;
}
const status = (w) => JSON.parse(w.__ew.status());
const step = (w, n = 1) => { for (let i = 0; i < n; i++) w.__ew.step(); return status(w); };
const val = (w, sel, i = 0) => w.document.querySelectorAll(sel)[i];

const FORM = (n = 1) => `<form>${Array.from({ length: n }, (_, i) => guestBlock(i)).join('')}<button type="submit" class="btn continue btn-PurchaseBlue" data-id="submit">המשך</button></form>`;

test('fills guest fields including Hebrew name, ID type and gender', () => {
  const w = load(FORM());
  step(w);
  assert.strictEqual(val(w, '#name0').value, 'דנה לוי');
  assert.strictEqual(val(w, '#guestId0').value, '123456782');
  assert.strictEqual(val(w, '[ng-model="guest.phone"]').value, '0501234567');
  assert.strictEqual(val(w, '[ng-model="guest.email"]').value, 'dana@example.com');
  assert.match(val(w, '#gender0').selectedOptions[0].textContent, /נקבה/);
  assert.match(val(w, '#guestIdSelect0').selectedOptions[0].textContent, /זהות/);
});

test('fills male gender correctly (male must not match female)', () => {
  const w = load(FORM(), { profile: { gender: 'MALE' } });
  step(w);
  assert.match(val(w, '#gender0').selectedOptions[0].textContent, /זכר/);
});

test('fills every guest form when buying several tickets', () => {
  const w = load(FORM(3));
  step(w);
  for (let i = 0; i < 3; i++) {
    assert.strictEqual(val(w, `#name${i}`).value, 'דנה לוי');
    assert.strictEqual(val(w, `#guestId${i}`).value, '123456782');
  }
});

test('answers the smoker question and ticks only required checkboxes', () => {
  const w = load(FORM() + `
    <div class="q"><label>מעשנים?</label><label><input type="radio" name="s0" value="yes" data-id="smoke-yes"> כן</label><label><input type="radio" name="s0" value="no" data-id="smoke-no"> לא</label></div>
    <div class="q"><label for="c1">אני מאשר/ת קבלת הודעה מחברת דלתא</label><input type="checkbox" id="c1" required></div>
    <div class="q"><label for="c2">אני מסכים לקבל פרסומים</label><input type="checkbox" id="c2"></div>`);
  step(w);
  assert.strictEqual(w.document.querySelector('[data-id="smoke-no"]').checked, true);
  assert.strictEqual(w.document.querySelector('[data-id="smoke-yes"]').checked, false);
  assert.strictEqual(w.document.getElementById('c1').checked, true);
  assert.strictEqual(w.document.getElementById('c2').checked, false);
});

test('fills social links, age and city questions by their text', () => {
  const w = load(FORM() + `
    <div class="q"><label for="a">קישור לפרופיל אינסטגרם</label><input type="text" id="a" required></div>
    <div class="q"><label for="b">Facebook profile</label><input type="text" id="b" required></div>
    <div class="q"><label for="c">גיל</label><input type="number" id="c" required></div>
    <div class="q"><label for="d">אזור מגורים</label><input type="text" id="d" required></div>`);
  step(w);
  assert.strictEqual(w.document.getElementById('a').value, 'https://instagram.com/dana');
  assert.strictEqual(w.document.getElementById('b').value, 'https://facebook.com/dana');
  assert.strictEqual(w.document.getElementById('c').value, '40');
  assert.strictEqual(w.document.getElementById('d').value, 'חיפה');
});

test('reports an unknown required question instead of guessing', () => {
  const w = load(FORM() + `<div class="q"><label for="z">מה הצבע האהוב עליך?</label><input type="text" id="z" required></div>`);
  const s = step(w, 2);
  assert.strictEqual(s.phase, 'needs-input');
  assert.ok(s.missing.some((m) => m.includes('הצבע האהוב')));
  assert.strictEqual(w.document.getElementById('z').value, '');
  assert.ok(!w.__clicks.includes('submit'), 'must not submit with a missing answer');
});

test('submits once everything required is filled', () => {
  const w = load(FORM());
  step(w, 2);
  assert.ok(w.__clicks.includes('submit'));
  assert.strictEqual(status(w).phase, 'submitting');
});

test('waits when no tickets are available yet', () => {
  const w = load(`<button data-id="plus" ng-click="changeTicketTypeAmount(ticketType,1)" disabled>+</button>`);
  assert.strictEqual(step(w).phase, 'waiting');
  assert.strictEqual(w.__clicks.length, 0);
});

test('selects tickets up to the requested count then continues', () => {
  const w = load(`<button data-id="plus" ng-click="changeTicketTypeAmount(ticketType,1)">+</button>
    <button data-id="minus" ng-click="changeTicketTypeAmount(ticketType,-1)">-</button>
    <button data-id="next" class="btn continue btn-PurchaseBlue" type="button">המשך</button>`);
  step(w, 3);
  assert.strictEqual(w.__clicks.filter((c) => c === 'plus').length, 2);
  assert.strictEqual(w.__clicks.filter((c) => c === 'minus').length, 0);
  assert.ok(w.__clicks.includes('next'));
});

test('stops adding tickets when the plus button becomes disabled', () => {
  const w = load(`<button data-id="plus" ng-click="changeTicketTypeAmount(ticketType,1)">+</button>
    <button data-id="next" class="btn continue btn-PurchaseBlue" type="button">המשך</button>`, { profile: { ticketCount: 5 } });
  w.document.addEventListener('click', (e) => { if (e.target.getAttribute('data-id') === 'plus' && w.__clicks.filter((c) => c === 'plus').length >= 3) e.target.disabled = true; }, true);
  step(w, 3);
  assert.strictEqual(w.__clicks.filter((c) => c === 'plus').length, 3);
});

test('detects the payment frame, stops, and never touches it', () => {
  const w = load(`<iframe src="https://payments.payplus.co.il/pay/abc" data-id="pay"></iframe><button data-id="pay-btn" class="btn continue btn-PurchaseBlue">שלם</button>`);
  assert.strictEqual(step(w).phase, 'payment');
  step(w, 3);
  assert.deepStrictEqual(w.__clicks, []);
});

test('detects a visible captcha challenge and hands over', () => {
  const w = load(FORM() + `<iframe src="https://www.google.com/recaptcha/api2/bframe?hl=he" title="recaptcha challenge"></iframe>`);
  assert.strictEqual(step(w).phase, 'human');
  assert.ok(!w.__clicks.includes('submit'));
});

test('ignores the invisible captcha badge frame', () => {
  const w = load(FORM() + `<iframe src="https://www.google.com/recaptcha/api2/anchor?size=invisible" title="reCAPTCHA"></iframe>`);
  assert.notStrictEqual(step(w, 2).phase, 'human');
});

test('does not fill hidden guest forms', () => {
  const w = load(`<div style="display:none">${guestBlock(0)}</div>`);
  step(w);
  assert.strictEqual(val(w, '#name0').value, '');
});
