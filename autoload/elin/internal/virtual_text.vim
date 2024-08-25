
function! elin#internal#virtual_text#set(text, ...) abort
  let opt = get(a:, 1, {})
  let bufnr = get(opt, 'bufnr', bufnr('%'))
  let lnum = get(opt, 'lnum', line('.'))
  let hl = get(opt, 'highlight', 'Normal')
  let align = get(opt, 'align', 'after')
  let close_after = get(opt, 'close-after', -1)

  let id = s:set(bufnr, a:text, lnum, hl, align)

  if close_after > 0
    call timer_start(close_after, {-> elin#internal#virtual_text#clear({'bufnr': bufnr, 'lnum': lnum})})
  endif

  return id
endfunction

function! elin#internal#virtual_text#delete(id, ...) abort
  let opt = get(a:, 1, {})
  let bufnr = get(opt, 'bufnr', bufnr('%'))
  return s:delete(bufnr, a:id)
endfunction

function! elin#internal#virtual_text#clear(...) abort
  let opt = get(a:, 1, {})
  let bufnr = get(opt, 'bufnr', bufnr('%'))
  let lnum = get(opt, 'lnum', v:null)

  if lnum is# v:null
    return s:clear(bufnr, 1, line('$'))
  else
    return s:clear(bufnr, lnum, lnum + 1)
  endif
endfunction

if has('nvim')
  let s:namespace = nvim_create_namespace('elin_virtual_text_namespace')

  function! s:text_align_to_virt_text_pos(align) abort
    return get({'after': 'eol', 'right': 'right_align'}, a:align, 'eol')
  endfunction

  function! s:set(bufnr, text, lnum, highlight, align) abort
    call s:clear(a:bufnr, a:lnum, a:lnum + 1)
    return nvim_buf_set_extmark(a:bufnr, s:namespace, a:lnum - 1, 0, {
          \ 'virt_text': [[a:text, a:highlight]],
          \ 'virt_text_pos': s:text_align_to_virt_text_pos(a:align)
          \ })
  endfunction

  function! s:delete(bufnr, id) abort
    return nvim_buf_del_extmark(a:bufnr, s:namespace, a:id)
  endfunction

  function! s:clear(bufnr, start_lnum, end_lnum) abort
    return nvim_buf_clear_namespace(a:bufnr, s:namespace, a:start_lnum - 1, a:end_lnum - 1)
  endfunction


else

  let s:textprop_type = 'elin_virtual_text_namespace'
  call prop_type_delete(s:textprop_type, {})
  call prop_type_add(s:textprop_type, {})

  function! s:set(bufnr, text, lnum, highlight, align) abort
    call s:clear(a:bufnr, a:lnum, a:lnum + 1)
    call prop_type_change(s:textprop_type, {'highlight': a:highlight})
    let id = prop_add(a:lnum, 0, {
          \ 'type': s:textprop_type,
          \ 'bufnr': a:bufnr,
          \ 'text': a:text,
          \ 'text_align': a:align,
          \ 'text_padding_left': 1,
          \ })
    redraw
    return id
  endfunction

  function! s:delete(bufnr, id) abort
    return prop_remove({'bufnr': a:bufnr, 'id': a:id})
  endfunction

  function! s:clear(bufnr, start_lnum, end_lnum) abort
    return prop_remove({
          \ 'type': s:textprop_type,
          \ 'bufnr': a:bufnr,
          \ 'all': v:true,
          \ }, a:start_lnum, a:end_lnum)
  endfunction

endif
