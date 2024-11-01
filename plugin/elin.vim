if exists('g:loaded_vim_elin')
  finish
endif
let g:loaded_vim_elin = 1

let g:elin_home = expand('<sfile>:p:h:h')
let g:elin_auto_connect = get(g:, 'elin_auto_connect', v:true)
let g:elin_server_port = get(g:, 'elin_server_port', v:null)

if !exists('g:elin_default_key_mapping_leader')
  let g:elin_default_key_mapping_leader = '<Leader>'
endif

" Initialize {{{
function! s:init() abort
  if &ft !=# 'clojure' || exists('g:initialized_vim_elin')
    return
  endif
  let g:initialized_vim_elin = 1

  " Initialize internal buffers
  try
    let &eventignore = 'WinEnter,WinLeave,BufEnter,BufLeave'
    call elin#internal#buffer#info#ready()
    call elin#internal#buffer#temp#ready()
  finally
    let &eventignore = ''
  endtry

  if g:elin_server_port is v:null
    call elin#server#start()
  endif

  if g:elin_auto_connect is v:true
    call elin#server#connect(g:elin_server_port)
  endif

  " FIXME this should be configurable by .elin.edn
  exe ':sign define elin_error text=🔥 texthl=ErrorMsg'

  aug elin_autocmd_group
    au!
    au FileType clojure setl omnifunc=elin#complete#omni
    au BufEnter *.clj,*.cljs,*.cljc,*.cljd call elin#intercept_notify('BufEnter')
    au BufNewFile *.clj,*.cljs,*.cljc,*.cljd call elin#intercept_notify('BufNewFile')
    au BufRead *.clj,*.cljs,*.cljc,*.cljd call elin#intercept_notify('BufRead')
    au BufWritePost *.clj,*.cljs,*.cljc,*.cljd call elin#intercept_notify('BufWritePost')
    au BufWritePre *.clj,*.cljs,*.cljc,*.cljd call elin#intercept_request('BufWritePre')
    au CursorMovedI *.clj,*.cljs,*.cljc,*.cljd call elin#intercept_request('CursorMovedI')
    au VimLeave * call s:deinit()
  aug END
endfunction

function! s:deinit() abort
  call elin#intercept_request('VimLeave')
  call elin#server#disconnect()
  if g:elin_server_port is v:null
    call elin#server#stop()
  endif
endfunction

function! s:startup() abort
  call s:init()
  aug elin_initializing_group
    au!
    au FileType clojure call s:init()
  aug END
endfunction

if has('vim_starting')
  aug elin_starting_group
    au!
    au VimEnter * call s:startup()
  aug END
else
  if &ft ==# 'clojure'
    call s:startup()
  endif
endif
" }}}

" Commands {{{

" Connection
command! -nargs=? ElinConnect call elin#notify('elin.handler.connect/connect', <q-args> ? [str2nr(<q-args>)] : [])
command! -nargs=1 ElinInstantConnect call elin#notify('elin.handler.connect/instant', [<q-args>])
command! ElinDisconnect call elin#notify('elin.handler.connect/disconnect', [])
command! ElinJackIn call elin#notify('elin.handler.connect/jack-in', [])
command! ElinSwitchConnection call elin#notify('elin.handler.connect/switch', [])

" Evaluation
command! -nargs=1 ElinEval call elin#notify('elin.handler.evaluate/evaluate', [<q-args>])
command! ElinEvalCurrentExpr call elin#notify('elin.handler.evaluate/evaluate-current-expr', [])
command! ElinEvalCurrentList call elin#notify('elin.handler.evaluate/evaluate-current-list', [])
command! ElinEvalCurrentTopList call elin#notify('elin.handler.evaluate/evaluate-current-top-list', [])
command! ElinEvalCurrentBuffer call elin#notify('elin.handler.evaluate/evaluate-current-buffer', [])
command! ElinEvalNsForm call elin#notify('elin.handler.evaluate/evaluate-namespace-form', [])
command! ElinEvalAtMark call elin#notify('elin.handler.evaluate/evaluate-at-mark', [nr2char(getchar())])
command! ElinEvalInContext call elin#notify('elin-alias-evaluate-current-list-in-context', [])
command! ElinPrintLastResult call elin#notify('elin.handler.evaluate/print-last-result', [])

command! ElinInterrupt call elin#notify('elin.handler.evaluate/interrupt', [])
command! ElinUndef call elin#notify('elin.handler.evaluate/undef', [])
command! ElinUndefAll call elin#notify('elin.handler.evaluate/undef-all', [])

command! ElinReload call elin#notify('elin.handler.evaluate/reload', [])
command! ElinReloadAll call elin#notify('elin.handler.evaluate/reload-all', [])

command! ElinMacroExpand1CurrentList call elin#notify('elin.handler.evaluate/expand-1-current-list', [])

" Refactoring
command! ElinAddLibspec call elin#notify('elin.handler.namespace/add-libspec', [])
command! ElinAddMissingLibspec call elin#notify('elin.handler.namespace/add-missing-libspec', [])

" Navigation
command! ElinJumpToDefinition call elin#notify('elin.handler.navigate/jump-to-definition', [])
command! ElinReferences call elin#notify('elin.handler.navigate/references', [])
command! ElinLocalReferences call elin#notify('elin.handler.navigate/local-references', [])

" Documentation
command! ElinLookup call elin#notify('elin.handler.lookup/lookup', [])
command! ElinShowSource call elin#notify('elin.handler.lookup/show-source', [])
command! ElinShowClojureDocs call elin#notify('elin.handler.lookup/show-clojuredocs', [])

" Testing
command! ElinTestUnderCursor call elin#notify('elin.handler.test/run-test-under-cursor', [])
command! ElinTestFocusedCurrentTesting call elin#notify('elin-alias-run-test-focused-current-testing', [])
command! ElinTestInNs call elin#notify('elin.handler.test/run-tests-in-ns', [])
command! ElinTestLast call elin#notify('elin.handler.test/rerun-last-tests', [])
command! ElinTestLastFailed call elin#notify('elin.handler.test/rerun-last-failed-tests', [])
command! ElinCycleSourceAndTest call elin#notify('elin.handler.navigate/cycle-source-and-test', [])

" Misc
command! ElinToggleInfoBuffer call elin#internal#buffer#info#toggle()
command! ElinClearInfoBuffer call elin#internal#buffer#info#clear()
command! ElinClearVirtualTexts call elin#internal#virtual_text#clear()
" }}}

" Mappings {{{

" Connection
nnoremap <silent> <Plug>(elin_connect) <Cmd>ElinConnect<CR>
nnoremap <silent> <Plug>(elin_jack_in) <Cmd>ElinJackIn<CR>
nnoremap <silent> <Plug>(elin_switch_connection) <Cmd>ElinSwitchConnection<CR>

" Evaluation
nnoremap <silent> <Plug>(elin_eval_current_expr) <Cmd>ElinEvalCurrentExpr<CR>
nnoremap <silent> <Plug>(elin_eval_current_list) <Cmd>ElinEvalCurrentList<CR>
nnoremap <silent> <Plug>(elin_eval_current_top_list) <Cmd>ElinEvalCurrentTopList<CR>
nnoremap <silent> <Plug>(elin_eval_current_buffer) <Cmd>ElinEvalCurrentBuffer<CR>
nnoremap <silent> <Plug>(elin_eval_ns_form) <Cmd>ElinEvalNsForm<CR>
nnoremap <silent> <Plug>(elin_eval_at_mark) <Cmd>ElinEvalAtMark<CR>
nnoremap <silent> <Plug>(elin_eval_in_context) <Cmd>ElinEvalInContext<CR>
nnoremap <silent> <Plug>(elin_print_last_result) <Cmd>ElinPrintLastResult<CR>

nnoremap <silent> <Plug>(elin_interrupt) <Cmd>ElinInterrupt<CR>
nnoremap <silent> <Plug>(elin_undef) <Cmd>ElinUndef<CR>
nnoremap <silent> <Plug>(elin_undef_all) <Cmd>ElinUndefAll<CR>

nnoremap <silent> <Plug>(elin_reload) <Cmd>ElinReload<CR>
nnoremap <silent> <Plug>(elin_reload_all) <Cmd>ElinReloadAll<CR>

nnoremap <silent> <Plug>(elin_macro_expand1_current_list) <Cmd>ElinMacroExpand1CurrentList<CR>

" Refactoring
nnoremap <silent> <Plug>(elin_add_libspec) <Cmd>ElinAddLibspec<CR>
nnoremap <silent> <Plug>(elin_add_missing_libspec) <Cmd>ElinAddMissingLibspec<CR>

" Navigation
nnoremap <silent> <Plug>(elin_jump_to_definition) <Cmd>ElinJumpToDefinition<CR>
nnoremap <silent> <Plug>(elin_references) <Cmd>ElinReferences<CR>
nnoremap <silent> <Plug>(elin_local_references) <Cmd>ElinLocalReferences<CR>

" Documentation
nnoremap <silent> <Plug>(elin_lookup) <Cmd>ElinLookup<CR>
nnoremap <silent> <Plug>(elin_show_source) <Cmd>ElinShowSource<CR>
nnoremap <silent> <Plug>(elin_show_clojuredocs) <Cmd>ElinShowClojureDocs<CR>

" Testing
nnoremap <silent> <Plug>(elin_test_under_cursor) <Cmd>ElinTestUnderCursor<CR>
nnoremap <silent> <Plug>(elin_test_focused_current_testing) <Cmd>ElinTestFocusedCurrentTesting<CR>
nnoremap <silent> <Plug>(elin_test_in_ns) <Cmd>ElinTestInNs<CR>
nnoremap <silent> <Plug>(elin_test_last) <Cmd>ElinTestLast<CR>
nnoremap <silent> <Plug>(elin_test_last_failed) <Cmd>ElinTestLastFailed<CR>
nnoremap <silent> <Plug>(elin_cycle_source_and_test) <Cmd>ElinCycleSourceAndTest<CR>

" Misc
nnoremap <silent> <Plug>(elin_toggle_info_buffer) <Cmd>ElinToggleInfoBuffer<CR>
nnoremap <silent> <Plug>(elin_clear_info_buffer) <Cmd>ElinClearInfoBuffer<CR>
nnoremap <silent> <Plug>(elin_clear_virtual_texts) <Cmd>ElinClearVirtualTexts<CR>
" }}}

" Default key mappings {{{
if exists('g:elin_enable_default_key_mappings')
      \ && g:elin_enable_default_key_mappings
  silent! call s:default_key_mappings()
  aug elin_default_key_mappings
    au!
    au FileType clojure call s:default_key_mappings()
  aug END
endif

function! s:define_mapping(map_type, default_keys, plug_name) abort
  if !hasmapto(a:plug_name)
    let keys = substitute(a:default_keys, '<Leader>', g:elin_default_key_mapping_leader, '')
    let cmd = printf('%s <buffer> %s %s',
          \ a:map_type,
          \ keys,
          \ a:plug_name,
          \ )
    call execute(cmd, 'silent!')
  endif
endfunction

function! s:default_key_mappings() abort
  if exists('b:elin_default_key_mappings_applied')
    return
  endif
  let b:elin_default_key_mappings_applied = v:true

  " Connection
  call s:define_mapping('nmap', "<Leader>'", '<Plug>(elin_connect)')
  call s:define_mapping('nmap', '<Leader>"', '<Plug>(elin_jack_in)')

  " Evaluation
  call s:define_mapping('nmap', '<Leader>ei', '<Plug>(elin_eval_current_expr)')
  call s:define_mapping('nmap', '<Leader>ee', '<Plug>(elin_eval_current_list)')
  call s:define_mapping('nmap', '<Leader>et', '<Plug>(elin_eval_current_top_list)')
  call s:define_mapping('nmap', '<Leader>eb', '<Plug>(elin_eval_current_buffer)')
  call s:define_mapping('nmap', '<Leader>en', '<Plug>(elin_eval_ns_form)')
  call s:define_mapping('nmap', '<Leader>ea', '<Plug>(elin_eval_at_mark)')
  call s:define_mapping('nmap', '<Leader>ece', '<Plug>(elin_eval_in_context)')
  call s:define_mapping('nmap', '<Leader>ep', '<Plug>(elin_print_last_result)')

  call s:define_mapping('nmap', '<Leader>eq', '<Plug>(elin_interrupt)')
  call s:define_mapping('nmap', '<Leader>eu', '<Plug>(elin_undef)')
  call s:define_mapping('nmap', '<Leader>eU', '<Plug>(elin_undef_all)')

  call s:define_mapping('nmap', '<Leader>enr', '<Plug>(elin_reload)')
  call s:define_mapping('nmap', '<Leader>enR', '<Plug>(elin_reload_all)')

  call s:define_mapping('nmap', '<Leader>em', '<Plug>(elin_macro_expand1_current_list)')

  " Refactoring
  call s:define_mapping('nmap', '<Leader>ran', '<Plug>(elin_add_libspec)')
  call s:define_mapping('nmap', '<Leader>ram', '<Plug>(elin_add_missing_libspec)')

  " Navigation
  call s:define_mapping('nmap', '<C-]>', '<Plug>(elin_jump_to_definition)')
  call s:define_mapping('nmap', '<Leader>br', '<Plug>(elin_references)')
  call s:define_mapping('nmap', '<Leader>blr', '<Plug>(elin_local_references)')

  " Documentation
  call s:define_mapping('nmap', 'K', '<Plug>(elin_lookup)')
  call s:define_mapping('nmap', '<Leader>hs', '<Plug>(elin_show_source)')
  call s:define_mapping('nmap', '<Leader>hc', '<Plug>(elin_show_clojuredocs)')

  " Testing
  call s:define_mapping('nmap', '<Leader>tt', '<Plug>(elin_test_under_cursor)')
  call s:define_mapping('nmap', '<Leader>tf', '<Plug>(elin_test_focused_current_testing)')
  call s:define_mapping('nmap', '<Leader>tn', '<Plug>(elin_test_in_ns)')
  call s:define_mapping('nmap', '<Leader>tl', '<Plug>(elin_test_last)')
  call s:define_mapping('nmap', '<Leader>tr', '<Plug>(elin_test_last_failed)')
  call s:define_mapping('nmap', 'tt', '<Plug>(elin_cycle_source_and_test)')

  " Misc
  call s:define_mapping('nmap', '<Leader>ss', '<Plug>(elin_toggle_info_buffer)')
  call s:define_mapping('nmap', '<Leader>sl', '<Plug>(elin_clear_info_buffer)')
  call s:define_mapping('nmap', '<Leader><Esc>', '<Plug>(elin_clear_virtual_texts)')
endfunction
" }}}

" vim:fdl=0:
