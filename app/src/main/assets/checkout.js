/*
 * EventerWatch checkout assistant. Injected into the Eventer purchase page inside the app's WebView.
 *
 * It does what a person would do, only faster: pick the ticket quantity, press continue, fill the guest
 * form and the event's questions from the buyer profile, tick required boxes, press continue again.
 * It STOPS (and tells the app) at the payment page, at a CAPTCHA challenge, and whenever the form asks
 * something it cannot answer. It never touches payment frames and never solves or bypasses CAPTCHAs.
 */
(function () {
  'use strict';
  if (window.__ew && window.__ew.version) return;

  var S = { profile: null, phase: 'waiting', detail: '', missing: [], timer: null, selected: 0, lastClick: 0, stopped: false };
  var PAY_HOST = /payplus|pelecard|payme|paypal|cardcom|tranzila|meshulam|stripe|braintree|creditguard/i;

  function qsa(sel, root) { return Array.prototype.slice.call((root || document).querySelectorAll(sel)); }
  function now() { return Date.now(); }
  function text(el) { return (el.textContent || '').replace(/\s+/g, ' ').trim(); }
  function setPhase(p, d) { S.phase = p; S.detail = d || ''; }

  function visible(el) {
    for (var n = el; n && n.nodeType === 1; n = n.parentElement) {
      if (n.hidden) return false;
      var cs = window.getComputedStyle(n);
      if (cs.display === 'none' || cs.visibility === 'hidden' || cs.opacity === '0') return false;
    }
    return true;
  }
  function enabled(el) { return !el.disabled && !el.hasAttribute('disabled'); }
  function fire(el, type) { el.dispatchEvent(new window.Event(type, { bubbles: true })); }

  function setValue(el, v) {
    v = String(v);
    if (el.value === v) return;
    var proto = el.tagName === 'TEXTAREA' ? window.HTMLTextAreaElement.prototype : window.HTMLInputElement.prototype;
    Object.getOwnPropertyDescriptor(proto, 'value').set.call(el, v); // works with AngularJS ng-model
    fire(el, 'input'); fire(el, 'change'); fire(el, 'blur');
  }

  function selectByText(sel, re, notRe) {
    var opts = qsa('option', sel);
    for (var i = 0; i < opts.length; i++) {
      var t = text(opts[i]);
      if (re.test(t) && !(notRe && notRe.test(t))) {
        if (sel.selectedIndex !== i) { sel.selectedIndex = i; fire(sel, 'change'); fire(sel, 'blur'); }
        return true;
      }
    }
    return false;
  }

  // ---- page state detection -------------------------------------------------------------------

  function paymentVisible() {
    if (PAY_HOST.test(location.hostname)) return true;
    return qsa('iframe').some(function (f) { return visible(f) && PAY_HOST.test(f.src || ''); });
  }
  function captchaVisible() {
    return qsa('iframe').some(function (f) {
      return /recaptcha\/api2\/bframe|hcaptcha\.com.*challenge|challenges\.cloudflare\.com/i.test(f.src || '') && visible(f);
    });
  }

  // ---- tickets ---------------------------------------------------------------------------------

  function plusButtons() {
    return qsa('button[ng-click*="changeTicketTypeAmount"]').filter(function (b) {
      return /changeTicketTypeAmount\([^,]+,\s*1\s*\)/.test(b.getAttribute('ng-click')) && visible(b);
    });
  }

  function clickContinue() {
    if (now() - S.lastClick < 1500) return;
    var cands = qsa('button, input[type=submit], a.btn').filter(function (b) {
      var nc = b.getAttribute('ng-click') || '';
      return visible(b) && enabled(b) && /btn-PurchaseBlue|continue/.test(b.className) &&
        !/changeTicketTypeAmount|acebook|remove|addPayment/i.test(nc);
    });
    if (cands.length) { S.lastClick = now(); cands[0].click(); }
  }

  function stepTickets(buttons) {
    var btn = buttons.filter(enabled)[0];
    if (!btn) return setPhase('waiting', 'Tickets are not on sale yet');
    var n = 0;
    while (S.selected < S.profile.ticketCount && enabled(btn) && n < 12) { btn.click(); S.selected++; n++; }
    if (S.selected >= S.profile.ticketCount || !enabled(btn)) {
      setPhase('continuing', S.selected + ' ticket(s) selected');
      clickContinue();
    } else {
      setPhase('selecting', S.selected + ' ticket(s) selected');
    }
  }

  // ---- guest form ------------------------------------------------------------------------------

  function guestEls(model) { return qsa('[ng-model="guest.' + model + '"]').filter(visible); }

  function fillGuests() {
    var p = S.profile;
    var names = guestEls('name'), ids = guestEls('id'), phones = guestEls('phone'), emails = guestEls('email'),
      genders = guestEls('isMale'), idTypes = guestEls('isPassportAsId'), ages = guestEls('age'), dobs = guestEls('dateOfBirth');
    names.forEach(function (el, i) {
      setValue(el, p.fullName);
      if (idTypes[i]) selectByText(idTypes[i], /זהות|ת\.?ז|israeli|\bid\b/i, /דרכון|passport/i) || (idTypes[i].selectedIndex = Math.max(0, idTypes[i].selectedIndex));
      if (ids[i]) setValue(ids[i], p.idNumber);
      if (phones[i]) setValue(phones[i], p.phone.replace(/\D/g, '').replace(/^972/, '0'));
      if (emails[i]) setValue(emails[i], p.email);
      if (genders[i] && p.gender) {
        if (p.gender === 'FEMALE') selectByText(genders[i], /נקבה|אישה|female|woman/i);
        else selectByText(genders[i], /זכר|גבר|male|\bman\b/i, /נקבה|female|woman/i);
      }
      if (ages[i] && p.age) setValue(ages[i], p.age);
      if (dobs[i] && p.birthDate) setValue(dobs[i], p.birthDate);
    });
    return names.length;
  }

  var GUEST_MODEL = /^guest\.(name|id|phone|email|isMale|isPassportAsId|age|dateOfBirth)$/;
  function isGuestCore(el) { return GUEST_MODEL.test(el.getAttribute('ng-model') || ''); }

  function members(el) {
    return el.type === 'radio' && el.name ? qsa('input[type=radio]').filter(function (r) { return r.name === el.name; }) : [el];
  }

  function context(el) {
    var parts = [el.placeholder || '', el.getAttribute('aria-label') || '', el.name || '', el.id || ''];
    if (el.id) { var l = document.querySelector('label[for="' + el.id + '"]'); if (l) parts.push(text(l)); }
    var group = members(el);
    var best = null;
    for (var p = el.parentElement; p; p = p.parentElement) {
      var ctrls = qsa('input,select,textarea', p).filter(function (c) { return c.type !== 'hidden'; });
      if (!ctrls.every(function (c) { return group.indexOf(c) >= 0; })) break;
      best = p;
    }
    if (best) parts.push(text(best));
    return parts.join(' ');
  }

  function labelOf(radio) {
    var l = radio.closest('label') || (radio.id && document.querySelector('label[for="' + radio.id + '"]'));
    return l ? text(l) : (radio.value || '');
  }

  function isRequired(el) { return el.required || el.getAttribute('aria-required') === 'true'; }

  function answerText(el, ctx, p) {
    var t = el.type;
    if (/instagram|אינסטגרם|\big\b|igprofile/i.test(ctx)) return p.instagramUrl;
    if (/facebook|פייסבוק|\bfb\b/i.test(ctx)) return p.facebookUrl;
    if (/גיל|\bage\b/i.test(ctx)) return p.age ? String(p.age) : '';
    if (/אזור|מגורים|עיר|ישוב|יישוב|city|area|living|location/i.test(ctx)) return p.city;
    if (/טלפון|phone|נייד/i.test(ctx) && t !== 'checkbox') return p.phone;
    if (/אימייל|דוא|e-?mail/i.test(ctx)) return p.email;
    return '';
  }

  function fillOthers() {
    var p = S.profile, done = {};
    qsa('input,select,textarea').filter(function (el) {
      return visible(el) && !isGuestCore(el) && enabled(el) &&
        ['hidden', 'button', 'submit', 'image', 'file', 'password', 'reset'].indexOf(el.type) < 0;
    }).forEach(function (el) {
      var ctx = context(el);
      if (el.type === 'radio') {
        var key = 'r:' + el.name;
        if (done[key]) return; done[key] = true;
        var group = members(el);
        if (group.some(function (r) { return r.checked; })) return;
        if (/מעשנ|smok/i.test(ctx) && p.smoker !== null && p.smoker !== undefined) {
          var want = p.smoker ? /^(כן|yes)$/i : /^(לא|no)$/i;
          var pick = group.filter(function (r) { return want.test(labelOf(r)); })[0];
          if (pick) pick.click();
        }
        return;
      }
      if (el.type === 'checkbox') {
        if (isRequired(el) && !el.checked) el.click(); // only the compulsory boxes; promos stay off
        return;
      }
      if (el.tagName === 'SELECT') {
        if (el.value === '' || el.selectedIndex <= 0) {
          var city = answerText(el, ctx, p);
          if (city) selectByText(el, new RegExp(city.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'i'));
        }
        return;
      }
      var v = answerText(el, ctx, p);
      if (v) setValue(el, v);
    });
  }

  function emptyRequired() {
    var out = [], seen = {};
    qsa('input,select,textarea').filter(function (el) {
      return visible(el) && enabled(el) && isRequired(el) && ['hidden', 'button', 'submit'].indexOf(el.type) < 0;
    }).forEach(function (el) {
      var empty;
      if (el.type === 'checkbox') empty = !el.checked;
      else if (el.type === 'radio') {
        if (seen['r:' + el.name]) return; seen['r:' + el.name] = true;
        empty = !members(el).some(function (r) { return r.checked; });
      } else if (el.tagName === 'SELECT') empty = el.value === '' || el.selectedIndex < 0;
      else empty = el.value === '';
      if (empty) out.push(context(el).replace(/\s+/g, ' ').trim().slice(0, 70));
    });
    // radio questions the page does not mark required but we could not answer are not blockers
    return out;
  }

  function findSubmit(anchor) {
    var form = anchor.closest('form');
    var scope = form || document;
    return qsa('button[type=submit], input[type=submit], .btn-PurchaseBlue', scope)
      .filter(function (b) { return visible(b) && enabled(b) && !/acebook|remove/i.test(b.getAttribute('ng-click') || ''); })[0];
  }

  function stepGuests(first) {
    fillGuests();
    fillOthers();
    var missing = emptyRequired();
    S.missing = missing;
    if (missing.length) return setPhase('needs-input', 'Needs you: ' + missing.join(' | '));
    var submit = findSubmit(first);
    if (submit && now() - S.lastClick >= 1500) {
      S.lastClick = now();
      setPhase('submitting', 'Details filled – continuing to payment');
      submit.click();
    } else if (S.phase !== 'submitting') {
      setPhase('filling', 'Details filled');
    }
  }

  // ---- main loop -------------------------------------------------------------------------------

  function step() {
    if (S.stopped) return;
    try {
      if (paymentVisible()) { setPhase('payment', 'Payment page reached – you complete the payment'); stop(); return; }
      if (captchaVisible()) { setPhase('human', 'A verification (CAPTCHA) needs you'); return; }
      var names = guestEls('name');
      if (names.length) { stepGuests(names[0]); return; }
      var plus = plusButtons();
      if (plus.length) { stepTickets(plus); return; }
      setPhase('waiting', 'Waiting for tickets to open');
    } catch (e) {
      setPhase('error', String(e && e.message || e));
    }
  }

  function stop() {
    S.stopped = true;
    if (S.timer) { clearInterval(S.timer); S.timer = null; }
  }

  window.__ew = {
    version: 1,
    start: function (profile, opts) {
      S.profile = profile; S.stopped = false; S.selected = 0;
      if (!(opts && opts.manual)) { S.timer = setInterval(step, 250); step(); }
    },
    step: step,
    stop: stop,
    status: function () {
      return JSON.stringify({ phase: S.phase, detail: S.detail, missing: S.missing, selected: S.selected });
    }
  };
})();
