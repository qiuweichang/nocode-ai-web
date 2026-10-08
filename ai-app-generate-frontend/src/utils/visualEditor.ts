/**
 * 预览页元素信息。
 * 该结构会附加到 AI 提示词中，使模型能够定位用户点击的页面模块。
 */
export interface ElementInfo {
  tagName: string
  id: string
  className: string
  textContent: string
  selector: string
  pagePath: string
}

/** 可视化编辑器回调配置。 */
export interface VisualEditorOptions {
  onElementSelected?: (elementInfo: ElementInfo) => void
}

/**
 * 同源 iframe 可视化元素选择器。
 * 负责向生成页面注入轻量选择脚本、同步编辑模式，并把点击元素信息回传给工作台。
 */
export class VisualEditor {
  /** 当前预览 iframe，只有同源预览才能注入选择脚本。 */
  private iframe: HTMLIFrameElement | null = null

  /** 是否处于元素选择模式，用于 iframe 重载后恢复选择状态。 */
  private editMode = false

  /** 选择结果回调配置。 */
  private readonly options: VisualEditorOptions

  /**
   * 创建可视化编辑器。
   *
   * @param options 元素选择回调配置
   */
  constructor(options: VisualEditorOptions = {}) {
    this.options = options
  }

  /**
   * 绑定预览 iframe。
   *
   * @param iframe 当前生成页面的 iframe 元素
   */
  init(iframe: HTMLIFrameElement) {
    this.iframe = iframe
  }

  /**
   * iframe 每次加载完成后重新注入选择器，确保源码保存触发重载后仍可继续编辑。
   */
  onIframeLoad() {
    if (this.editMode) {
      this.injectEditorScript()
    }
  }

  /**
   * 切换元素选择模式。
   *
   * @returns 切换后的编辑模式状态
   */
  toggleEditMode() {
    this.editMode = !this.editMode
    if (this.editMode) {
      this.injectEditorScript()
    } else {
      this.postToIframe({ type: 'NOCODE_TOGGLE_EDIT_MODE', editMode: false })
      this.postToIframe({ type: 'NOCODE_CLEAR_SELECTION' })
    }
    return this.editMode
  }

  /**
   * 主动退出元素选择模式并清理预览页中的描边效果。
   */
  disableEditMode() {
    this.editMode = false
    this.postToIframe({ type: 'NOCODE_TOGGLE_EDIT_MODE', editMode: false })
    this.postToIframe({ type: 'NOCODE_CLEAR_SELECTION' })
  }

  /**
   * 清除当前选中元素，但保留选择模式，方便用户立即选择另一个模块。
   */
  clearSelection() {
    this.postToIframe({ type: 'NOCODE_CLEAR_SELECTION' })
  }

  /**
   * 处理 iframe 发回的元素选择消息。
   * 仅接受当前 iframe 窗口的消息，避免页面中其他 frame 伪造选择结果。
   *
   * @param event 浏览器 message 事件
   */
  handleIframeMessage(event: MessageEvent) {
    if (!this.iframe?.contentWindow || event.source !== this.iframe.contentWindow) {
      return
    }
    if (event.data?.type === 'NOCODE_ELEMENT_SELECTED' && event.data?.elementInfo) {
      this.options.onElementSelected?.(event.data.elementInfo as ElementInfo)
    }
  }

  /**
   * 向预览 iframe 发送控制消息。
   * iframe 内容来自当前项目自己的静态服务，因此使用当前窗口 origin 作为目标源。
   */
  private postToIframe(payload: Record<string, unknown>) {
    this.iframe?.contentWindow?.postMessage(payload, window.location.origin)
  }

  /**
   * 注入页面选择脚本。
   * 开发环境通过 Vite 同源代理访问预览；若部署环境未配置同源静态路径，会给调用方抛出明确错误。
   */
  private injectEditorScript() {
    if (!this.iframe) {
      throw new Error('预览页面尚未加载')
    }
    try {
      const documentRef = this.iframe.contentDocument
      if (!documentRef?.head || !documentRef.body) {
        throw new Error('预览页面尚未准备完成')
      }
      const existingScript = documentRef.getElementById('nocode-visual-editor-script')
      if (existingScript) {
        this.postToIframe({ type: 'NOCODE_TOGGLE_EDIT_MODE', editMode: true })
        return
      }
      const script = documentRef.createElement('script')
      script.id = 'nocode-visual-editor-script'
      script.textContent = this.buildEditorScript()
      documentRef.head.appendChild(script)
    } catch (error) {
      this.editMode = false
      throw new Error('预览页面必须通过同源地址加载后才能选择元素')
    }
  }

  /**
   * 生成注入到预览页中的独立脚本。
   * 脚本通过捕获阶段拦截点击，避免选择模块时误触生成页面本身的链接或按钮。
   */
  private buildEditorScript() {
    return `
      (() => {
        let editMode = true;
        let hovered = null;
        let selected = null;

        const style = document.createElement('style');
        style.id = 'nocode-visual-editor-style';
        style.textContent = [
          '.nocode-edit-hover { outline: 2px dashed #2563eb !important; outline-offset: 2px !important; cursor: crosshair !important; }',
          '.nocode-edit-selected { outline: 3px solid #2563eb !important; outline-offset: 3px !important; cursor: crosshair !important; }'
        ].join('');
        document.head.appendChild(style);

        const clearHover = () => {
          if (hovered) hovered.classList.remove('nocode-edit-hover');
          hovered = null;
        };
        const clearSelection = () => {
          if (selected) selected.classList.remove('nocode-edit-selected');
          selected = null;
        };
        const selectable = (element) => element && element !== document.body && element !== document.documentElement && !['SCRIPT', 'STYLE'].includes(element.tagName);
        const selectorFor = (element) => {
          const segments = [];
          let current = element;
          while (current && current !== document.body) {
            let segment = current.tagName.toLowerCase();
            if (current.id) {
              segments.unshift(segment + '#' + CSS.escape(current.id));
              break;
            }
            const classes = Array.from(current.classList || []).filter((name) => !name.startsWith('nocode-edit-')).slice(0, 3);
            if (classes.length) segment += '.' + classes.map((name) => CSS.escape(name)).join('.');
            const siblings = current.parentElement ? Array.from(current.parentElement.children) : [];
            segment += ':nth-child(' + (siblings.indexOf(current) + 1) + ')';
            segments.unshift(segment);
            current = current.parentElement;
          }
          return segments.join(' > ');
        };
        const infoFor = (element) => ({
          tagName: element.tagName,
          id: element.id || '',
          className: typeof element.className === 'string' ? element.className.replace(/nocode-edit-(hover|selected)/g, '').trim() : '',
          textContent: (element.textContent || '').trim().replace(/\\s+/g, ' ').slice(0, 160),
          selector: selectorFor(element),
          pagePath: window.location.pathname + window.location.search + window.location.hash
        });

        document.addEventListener('mouseover', (event) => {
          if (!editMode || !selectable(event.target) || event.target === selected) return;
          clearHover();
          hovered = event.target;
          hovered.classList.add('nocode-edit-hover');
        }, true);
        document.addEventListener('mouseout', () => editMode && clearHover(), true);
        document.addEventListener('click', (event) => {
          if (!editMode || !selectable(event.target)) return;
          event.preventDefault();
          event.stopPropagation();
          clearHover();
          clearSelection();
          selected = event.target;
          selected.classList.add('nocode-edit-selected');
          window.parent.postMessage({ type: 'NOCODE_ELEMENT_SELECTED', elementInfo: infoFor(selected) }, window.location.origin);
        }, true);
        window.addEventListener('message', (event) => {
          if (event.origin !== window.location.origin) return;
          if (event.data?.type === 'NOCODE_TOGGLE_EDIT_MODE') {
            editMode = Boolean(event.data.editMode);
            if (!editMode) { clearHover(); clearSelection(); }
          }
          if (event.data?.type === 'NOCODE_CLEAR_SELECTION') clearSelection();
        });
      })();
    `
  }
}
