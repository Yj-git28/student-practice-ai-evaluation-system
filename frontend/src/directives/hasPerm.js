import { useUserStore } from '@/stores/useUserStore'

export default {
  mounted(el, binding) {
    const perms = useUserStore().permissions || []
    const need = Array.isArray(binding.value) ? binding.value : [binding.value]

    const pass = binding.modifiers.all
      ? need.every(p => perms.includes(p))
      : need.some(p => perms.includes(p))

    if (pass) return

    if (binding.modifiers.disable) {
      el.setAttribute('disabled', 'disabled')
      el.classList.add('is-disabled')
      el.style.pointerEvents = 'none'
      el.style.opacity = '0.5'
      el.title = '无操作权限'
    } else {
      el.parentNode && el.parentNode.removeChild(el)
    }
  }
}
