---
layout: default
title: Поддержать автора
permalink: /support/
---

«Досмотр» бесплатный и таким останется. Если приложение оказалось полезным, автору можно
перевести любую сумму — это никак не влияет на функции приложения: ничего не открывается,
ничего не даётся взамен, у переводивших нет и не будет никаких отличий внутри приложения.

Перевод — это подарок автору, а не оплата приложения, услуги или доработки.

{% assign has_any = false %}
{% if site.donate.cloudtips != "" or site.donate.sbp != "" or site.donate.usdt != "" %}
  {% assign has_any = true %}
{% endif %}

{% if has_any %}

{% if site.donate.cloudtips != "" %}
## Картой

<p><a class="btn" href="{{ site.donate.cloudtips }}">Перевести через CloudTips</a></p>
{% endif %}

{% if site.donate.sbp != "" %}
## СБП

Перевод по номеру телефона, без комиссии между физическими лицами.

<p>
  <code id="sbp">{{ site.donate.sbp }}</code>
  <button type="button" class="btn" data-copy="sbp">Скопировать</button>
</p>
{% endif %}

{% if site.donate.usdt != "" %}
## USDT (TRC-20)

Для тех, кому проще в криптовалюте. Сеть — Tron (TRC-20); перевод в другой сети до этого
адреса не дойдёт.

<p>
  <code id="usdt">{{ site.donate.usdt }}</code>
  <button type="button" class="btn" data-copy="usdt">Скопировать</button>
</p>

{% if site.donate.usdt_qr != "" %}
<p><img src="{{ site.donate.usdt_qr | relative_url }}" alt="QR-код адреса USDT TRC-20" width="220"></p>
{% endif %}
{% endif %}

<script>
  // Ровно одна вещь, которую страница делает помимо текста, и та своя: никаких внешних
  // скриптов, счётчиков и виджетов на сайте нет — ровно по той же причине, по которой их
  // нет в приложении.
  document.querySelectorAll('[data-copy]').forEach(function (button) {
    button.addEventListener('click', function () {
      var value = document.getElementById(button.dataset.copy).textContent.trim();
      navigator.clipboard.writeText(value).then(function () {
        var was = button.textContent;
        button.textContent = 'Скопировано';
        setTimeout(function () { button.textContent = was; }, 2000);
      });
    });
  });
</script>

{% else %}

## Способы перевода пока не подключены

Реквизиты появятся на этой странице, как только будут оформлены. Сейчас поддержать проект
можно иначе, и это не фигура речи: сообщение об ошибке с описанием того, что произошло,
экономит больше времени, чем небольшой перевод.

- [Issues проекта]({{ site.repo_url }}/issues) — ошибки и предложения.
- {{ site.contact_email }} — если писать в issues неудобно.

{% endif %}

## Почему этого нет в приложении из магазина

В сборках для RuStore и Google Play кнопок перевода нет и не будет: правила Google
требуют использовать их собственный биллинг для платежей внутри приложения, а RuStore —
соблюдения российского законодательства, включая часть 7 статьи 14 259-ФЗ, которая
запрещает распространять информацию о приёме цифровой валюты в качестве встречного
предоставления. Поэтому в магазинных сборках нет ни кнопок, ни самих адресов — они не
скрыты в интерфейсе, их нет в APK.

В сборке с [GitHub Releases]({{ site.releases_url }}) блок «Поддержать автора» есть прямо
в «О приложении». Правила магазинов на неё не распространяются — а требования закона
соблюдаются одинаково везде: ничего не даётся взамен и суммы не называются.

---

[На главную]({{ '/' | relative_url }}) ·
[Политика конфиденциальности]({{ '/privacy/' | relative_url }})
