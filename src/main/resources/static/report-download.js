(() => {
    let openDropdown = null;
    let nextId = 0;

    function closeDropdown(restoreFocus = false) {
        if (!openDropdown) return;
        const {container, toggle, menu} = openDropdown;
        menu.hidden = true;
        container.appendChild(menu);
        toggle.setAttribute('aria-expanded', 'false');
        openDropdown = null;
        if (restoreFocus) toggle.focus();
    }

    document.addEventListener('click', event => {
        const toggle = event.target.closest('.report-download-toggle');
        if (toggle) {
            const wasOpen = openDropdown?.toggle === toggle;
            closeDropdown();
            if (wasOpen) return;
            const container = toggle.closest('.report-download');
            const menu = container.querySelector('.report-download-menu');
            if (!menu) return;
            if (!menu.id) menu.id = `report-download-menu-${++nextId}`;
            toggle.setAttribute('aria-controls', menu.id);
            toggle.setAttribute('aria-expanded', 'true');
            // Keep the dropdown visible outside scrolling tables and panels.
            document.body.appendChild(menu);
            menu.hidden = false;
            const rect = toggle.getBoundingClientRect();
            const width = menu.offsetWidth;
            const height = menu.offsetHeight;
            menu.style.left = `${Math.max(8, Math.min(rect.right - width, window.innerWidth - width - 8))}px`;
            const top = rect.bottom + 6 + height <= window.innerHeight ? rect.bottom + 6 : rect.top - height - 6;
            menu.style.top = `${Math.max(8, top)}px`;
            openDropdown = {container, toggle, menu};
            menu.querySelector('a')?.focus();
        } else if (openDropdown && !openDropdown.menu.contains(event.target)) {
            closeDropdown();
        }
    });

    document.addEventListener('keydown', event => {
        if (event.key === 'Escape' && openDropdown) {
            event.preventDefault();
            closeDropdown(true);
        }
    });
    document.addEventListener('focusin', event => {
        if (openDropdown && !openDropdown.menu.contains(event.target) && !openDropdown.container.contains(event.target)) {
            closeDropdown();
        }
    });
    window.addEventListener('resize', () => closeDropdown());
    document.addEventListener('scroll', () => closeDropdown(), true);
})();
