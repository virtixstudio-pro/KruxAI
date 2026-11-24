function addCodeToolbars() {
    const codeBlocks = document.querySelectorAll('pre');
    codeBlocks.forEach((pre) => {
        if (pre.querySelector('.code-toolbar')) return;

        const toolbar = document.createElement('div');
        toolbar.className = 'code-toolbar';
        toolbar.style.cssText = 'display: flex; justify-content: flex-end; gap: 8px; background: #1e1e2e; padding: 6px 12px; border-radius: 8px 8px 0 0; border-bottom: 1px solid #313244;';

        const btnCopy = document.createElement('button');
        btnCopy.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-2px;margin-right:4px"><rect x="9" y="9" width="11" height="11" rx="2"/><path d="M5 15V6a2 2 0 0 1 2-2h9"/></svg>Copier';
        btnCopy.onclick = () => {
            const code = pre.querySelector('code') ? pre.querySelector('code').innerText : pre.innerText;
            if (window.AndroidInterface) {
                window.AndroidInterface.copyToClipboard(code);
            }
        };

        const btnShare = document.createElement('button');
        btnShare.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-2px;margin-right:4px"><path d="M12 15V3"/><path d="M7 8l5-5 5 5"/><path d="M5 13v6a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2v-6"/></svg>Partager';
        btnShare.onclick = () => {
            const code = pre.querySelector('code') ? pre.querySelector('code').innerText : pre.innerText;
            if (window.AndroidInterface) {
                window.AndroidInterface.shareCode(code);
            }
        };

        const btnDownload = document.createElement('button');
        btnDownload.innerHTML = '<svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" style="vertical-align:-2px;margin-right:4px"><path d="M12 3v12"/><path d="M7 10l5 5 5-5"/><path d="M5 21h14"/></svg>Télécharger';
        btnDownload.onclick = () => {
            const code = pre.querySelector('code') ? pre.querySelector('code').innerText : pre.innerText;
            if (window.AndroidInterface) {
                window.AndroidInterface.downloadCode(code, 'krux_script.txt');
            }
        };

        [btnCopy, btnShare, btnDownload].forEach(btn => {
            btn.style.cssText = 'background: #313244; color: #cdd6f4; border: none; padding: 4px 8px; border-radius: 4px; font-size: 12px; cursor: pointer;';
            toolbar.appendChild(btn);
        });

        pre.parentNode.insertBefore(toolbar, pre);
    });
}
document.addEventListener("DOMContentLoaded", addCodeToolbars);
