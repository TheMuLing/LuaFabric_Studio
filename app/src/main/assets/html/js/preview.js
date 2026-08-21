function preview(md_text) {
    if (!md_text) return;
    md_text = md_text.replace(/\\n/g, "\n");
    const html = marked.parse(md_text);
    document.getElementById('preview').innerHTML = html;
    // Render LaTeX with KaTeX auto-render
    if (typeof renderMathInElement === 'function') {
        renderMathInElement(document.getElementById('preview'), {
            delimiters: [
                {left: '$$', right: '$$', display: true},
                {left: '$', right: '$', display: false},
                {left: '\\(', right: '\\)', display: false},
                {left: '\\[', right: '\\]', display: true}
            ],
            throwOnError: false
        });
    }
    document.querySelectorAll('pre code').forEach((block) => {
        hljs.highlightElement(block);
    });
    addCopyButtonsAndLangLabels();
}

function addCopyButtonsAndLangLabels() {
    document.querySelectorAll('pre').forEach((preElement) => {
        if (preElement.querySelector('.code-header')) return;
        const codeElement = preElement.querySelector('code');
        if (!codeElement) return;
        const header = document.createElement('div');
        header.className = 'code-header';
        let lang = '';
        for (const className of codeElement.classList) {
            if (className.startsWith('language-')) {
                lang = className.replace('language-', '').toLowerCase();
                break;
            }
        }
        if (lang) {
            const langLabel = document.createElement('span');
            langLabel.className = 'code-language';
            langLabel.textContent = lang;
            header.appendChild(langLabel);
        }
        const copyButton = document.createElement('button');
        copyButton.className = 'copy-button';
        copyButton.textContent = 'Copy';
        copyButton.addEventListener('click', async () => {
            const code = codeElement.textContent || '';
            try {
                await navigator.clipboard.writeText(code);
                copyButton.textContent = 'Copied';
                copyButton.classList.add('copied');
                setTimeout(() => {
                    copyButton.textContent = 'Copy';
                    copyButton.classList.remove('copied');
                }, 2000);
            } catch (err) {
                copyButton.textContent = 'Failed';
                setTimeout(() => copyButton.textContent = 'Copy', 2000);
            }
        });
        header.appendChild(copyButton);
        preElement.appendChild(header);
    });
}