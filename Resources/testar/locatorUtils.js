(() => {
  window.__locatorUtils = {

    getRole: function (el) {
      try {
        let role = el.getAttribute('role');
        const tagName = el.tagName.toLowerCase();

        if (!role) {
          switch (tagName) {
            case 'button':
              role = 'button';
              break;
            case 'a':
              role = el.hasAttribute('href') ? 'link' : '';
              break;
            case 'input':
              const type = el.getAttribute('type')?.toLowerCase() || '';
              if (['checkbox', 'radio'].includes(type)) role = type;
              else if (['submit', 'button'].includes(type)) role = 'button';
              else role = 'textbox';
              break;
            case 'select':
              role = 'combobox';
              break;
            case 'textarea':
              role = 'textbox';
              break;
            default:
              role = '';
          }
        }

        if (!role) return ''; // skip unsupported

        const name =
          el.getAttribute('aria-label') ||
          el.ariaLabel ||
          el.getAttribute('title') ||
          el.innerText?.trim() ||
          el.value ||
          '';

        if (!name || name.trim() === '') return '';

        return `${role.toLowerCase()};${name.trim()}`;
      } catch (e) {
        return '';
      }
    },

    getXPath: function (element) {
      try {
        var path = [];
        while (element && element.nodeType === Node.ELEMENT_NODE) {
          var index = 1;
          var sibling = element.previousSibling;
          while (sibling) {
            if (sibling.nodeType === Node.ELEMENT_NODE && sibling.nodeName === element.nodeName) {
              index++;
            }
            sibling = sibling.previousSibling;
          }
          var tagName = element.nodeName.toLowerCase();
          var pathSegment = tagName + '[' + index + ']';
          path.unshift(pathSegment);
          element = element.parentNode;
        }
        return '/' + path.join('/');
      } catch (error) {
        return '';
      }
    },

    getText: function (el) {
      try {
        const text = el.innerText?.trim();
        if (!text || text.length === 0) return '';
        return text;
      } catch (e) {
        return '';
      }
    },

    getCSS: function (el) {
      let generatedCss = '';
      try {
        generatedCss = CssSelectorGenerator.getCssSelector(el);
      } catch (e) {
        generatedCss = el.tagName.toLowerCase() + (el.id ? '#' + el.id : '');
      }
      return generatedCss;
    },

    getLabel: function (el) {
      try {
        if (el.labels && el.labels.length > 0) return el.labels[0].innerText;
        if (el.getAttribute('aria-label')) return el.getAttribute('aria-label');
        if (el.getAttribute('aria-labelledby')) {
          const lbl = document.getElementById(el.getAttribute('aria-labelledby'));
          if (lbl) return lbl.innerText;
        }
        if (el.id) {
          const label = document.querySelector('label[for="' + el.id + '"]');
          if (label) return label.innerText;
        }
        // Fallback: look up to nearest <label>
        let parentLabel = el.closest('label');
        if (parentLabel) return parentLabel.innerText;
      } catch (e) {}
      return '';
    },

    getAltText: function (el) {
      return el.getAttribute('alt') ||
        el.getAttribute('aria-label') ||
        el.getAttribute('title') ||
        el.textContent?.trim() || '';
    },

    getTestId: function (el) {
      return el.getAttribute('data-testid') ||
        el.getAttribute('data-test-id') ||
        el.getAttribute('test-id') ||
        '';
    },

    extractLocators: function (el) {
      return {
        role: this.getRole(el),
        xpath: this.getXPath(el),
        text: this.getText(el),
        css: this.getCSS(el),
        placeholder: el.getAttribute('placeholder') || el.getAttribute('aria-placeholder') || '',
        label: this.getLabel(el),
        altText: el.getAttribute('alt') || '',
        title: el.getAttribute('title') || '',
        testId: this.getTestId(el)
      };
    }
  };

  console.log("__locatorUtils enhanced with smarter attributes");
})();