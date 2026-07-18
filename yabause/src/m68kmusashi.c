/*  Copyright 2007 Guillaume Duhamel

This file is part of Yabause.

Yabause is free software; you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation; either version 2 of the License, or
(at your option) any later version.

Yabause is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with Yabause; if not, write to the Free Software
Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301  USA
*/

/*! \file m68kmusashi.c
\brief Musashi 68000 interface.
*/

#include "m68kmusashi.h"
#include "musashi/m68k.h"
#include "m68kcore.h"
#include "musashi/m68kcpu.h"

extern int m68ki_initial_cycles;
extern uint m68ki_address_space;

typedef struct
{
   u32 dar[16];
   u32 ppc;
   u32 pc;
   u32 sp[7];
   u32 vbr;
   u32 sfc;
   u32 dfc;
   u32 cacr;
   u32 caar;
   u32 ir;
   u32 t1_flag;
   u32 t0_flag;
   u32 s_flag;
   u32 m_flag;
   u32 x_flag;
   u32 n_flag;
   u32 not_z_flag;
   u32 v_flag;
   u32 c_flag;
   u32 int_mask;
   u32 int_level;
   u32 int_cycles;
   u32 stopped;
   u32 pref_addr;
   u32 pref_data;
   u32 instr_mode;
   u32 run_mode;
   u32 address_space;
   s32 remaining_cycles;
   s32 initial_cycles;
   u32 tracing;
} M68KMusashiState;

struct ReadWriteFuncs
{
   M68K_READ  *r_8;
   M68K_READ  *r_16;
   M68K_WRITE *w_8;
   M68K_WRITE *w_16;
}rw_funcs;

static int M68KMusashiInit(void) {

   m68k_init();
   m68k_set_reset_instr_callback(m68k_pulse_reset);
   m68k_set_cpu_type(M68K_CPU_TYPE_68000);

   return 0;
}

static void M68KMusashiDeInit(void) {
}

static void M68KMusashiReset(void) {
   m68k_pulse_reset();
}

static s32 FASTCALL M68KMusashiExec(s32 cycle) {
   return m68k_execute(cycle);
}

static void M68KMusashiSync(void) {
}

static u32 M68KMusashiGetDReg(u32 num) {
   return m68k_get_reg(NULL, M68K_REG_D0 + num);
}

static u32 M68KMusashiGetAReg(u32 num) {
   return m68k_get_reg(NULL, M68K_REG_A0 + num);
}

static u32 M68KMusashiGetPC(void) {
   return m68k_get_reg(NULL, M68K_REG_PC);
}

static u32 M68KMusashiGetSR(void) {
   return m68k_get_reg(NULL, M68K_REG_SR);
}

static u32 M68KMusashiGetUSP(void) {
   return m68k_get_reg(NULL, M68K_REG_USP);
}

static u32 M68KMusashiGetMSP(void) {
   return m68k_get_reg(NULL, M68K_REG_MSP);
}

static void M68KMusashiSetDReg(u32 num, u32 val) {
   m68k_set_reg(M68K_REG_D0 + num, val);
}

static void M68KMusashiSetAReg(u32 num, u32 val) {
   m68k_set_reg(M68K_REG_A0 + num, val);
}

static void M68KMusashiSetPC(u32 val) {
   m68k_set_reg(M68K_REG_PC, val);
}

static void M68KMusashiSetSR(u32 val) {
   m68k_set_reg(M68K_REG_SR, val);
}

static void M68KMusashiSetUSP(u32 val) {
   m68k_set_reg(M68K_REG_USP, val);
}

static void M68KMusashiSetMSP(u32 val) {
   m68k_set_reg(M68K_REG_MSP, val);
}

static void M68KMusashiSetFetch(u32 low_adr, u32 high_adr, pointer fetch_adr) {
}

static void FASTCALL M68KMusashiSetIRQ(s32 level) {
   if (level > 0)
      m68k_set_irq(level);
}

static void FASTCALL M68KMusashiWriteNotify(u32 address, u32 size) {
}

unsigned int  m68k_read_memory_8(unsigned int address)
{
   return rw_funcs.r_8(address);
}

unsigned int  m68k_read_memory_16(unsigned int address)
{
   return rw_funcs.r_16(address);
}

unsigned int  m68k_read_memory_32(unsigned int address)
{
   u16 val1 = rw_funcs.r_16(address);

   return (val1 << 16 | rw_funcs.r_16(address + 2));
}

void m68k_write_memory_8(unsigned int address, unsigned int value)
{
   rw_funcs.w_8(address, value);
}

void m68k_write_memory_16(unsigned int address, unsigned int value)
{
   rw_funcs.w_16(address, value);
}

void m68k_write_memory_32(unsigned int address, unsigned int value)
{
   rw_funcs.w_16(address, value >> 16 );
   rw_funcs.w_16(address + 2, value & 0xffff);
}

static void M68KMusashiSetReadB(M68K_READ *Func) {
   rw_funcs.r_8 = Func;
}

static void M68KMusashiSetReadW(M68K_READ *Func) {
   rw_funcs.r_16 = Func;
}

static void M68KMusashiSetWriteB(M68K_WRITE *Func) {
   rw_funcs.w_8 = Func;
}

static void M68KMusashiSetWriteW(M68K_WRITE *Func) {
   rw_funcs.w_16 = Func;
}

static void M68KMusashiSaveState(StateStream *fp) {
   IOCheck_struct check = { 0, 0 };
   M68KMusashiState state;

   memcpy(state.dar, m68ki_cpu.dar, sizeof(state.dar));
   state.ppc = m68ki_cpu.ppc;
   state.pc = m68ki_cpu.pc;
   memcpy(state.sp, m68ki_cpu.sp, sizeof(state.sp));
   state.vbr = m68ki_cpu.vbr;
   state.sfc = m68ki_cpu.sfc;
   state.dfc = m68ki_cpu.dfc;
   state.cacr = m68ki_cpu.cacr;
   state.caar = m68ki_cpu.caar;
   state.ir = m68ki_cpu.ir;
   state.t1_flag = m68ki_cpu.t1_flag;
   state.t0_flag = m68ki_cpu.t0_flag;
   state.s_flag = m68ki_cpu.s_flag;
   state.m_flag = m68ki_cpu.m_flag;
   state.x_flag = m68ki_cpu.x_flag;
   state.n_flag = m68ki_cpu.n_flag;
   state.not_z_flag = m68ki_cpu.not_z_flag;
   state.v_flag = m68ki_cpu.v_flag;
   state.c_flag = m68ki_cpu.c_flag;
   state.int_mask = m68ki_cpu.int_mask;
   state.int_level = m68ki_cpu.int_level;
   state.int_cycles = m68ki_cpu.int_cycles;
   state.stopped = m68ki_cpu.stopped;
   state.pref_addr = m68ki_cpu.pref_addr;
   state.pref_data = m68ki_cpu.pref_data;
   state.instr_mode = m68ki_cpu.instr_mode;
   state.run_mode = m68ki_cpu.run_mode;
   state.address_space = m68ki_address_space;
   state.remaining_cycles = m68ki_remaining_cycles;
   state.initial_cycles = m68ki_initial_cycles;
   state.tracing = m68ki_tracing;

   StateWriteChecked(&check, &state, sizeof(state), 1, fp);
}

static void M68KMusashiLoadState(StateStream *fp) {
   IOCheck_struct check = { 0, 0 };
   M68KMusashiState state;

   StateReadChecked(&check, &state, sizeof(state), 1, fp);
   if (fp->failed)
      return;

   /* Keep Musashi's CPU configuration, cycle tables and host callbacks. */
   memcpy(m68ki_cpu.dar, state.dar, sizeof(state.dar));
   m68ki_cpu.ppc = state.ppc;
   m68ki_cpu.pc = state.pc;
   memcpy(m68ki_cpu.sp, state.sp, sizeof(state.sp));
   m68ki_cpu.vbr = state.vbr;
   m68ki_cpu.sfc = state.sfc;
   m68ki_cpu.dfc = state.dfc;
   m68ki_cpu.cacr = state.cacr;
   m68ki_cpu.caar = state.caar;
   m68ki_cpu.ir = state.ir;
   m68ki_cpu.t1_flag = state.t1_flag;
   m68ki_cpu.t0_flag = state.t0_flag;
   m68ki_cpu.s_flag = state.s_flag;
   m68ki_cpu.m_flag = state.m_flag;
   m68ki_cpu.x_flag = state.x_flag;
   m68ki_cpu.n_flag = state.n_flag;
   m68ki_cpu.not_z_flag = state.not_z_flag;
   m68ki_cpu.v_flag = state.v_flag;
   m68ki_cpu.c_flag = state.c_flag;
   m68ki_cpu.int_mask = state.int_mask;
   m68ki_cpu.int_level = state.int_level;
   m68ki_cpu.int_cycles = state.int_cycles;
   m68ki_cpu.stopped = state.stopped;
   m68ki_cpu.pref_addr = state.pref_addr;
   m68ki_cpu.pref_data = state.pref_data;
   m68ki_cpu.instr_mode = state.instr_mode;
   m68ki_cpu.run_mode = state.run_mode;
   m68ki_address_space = state.address_space;
   m68ki_remaining_cycles = state.remaining_cycles;
   m68ki_initial_cycles = state.initial_cycles;
   m68ki_tracing = state.tracing;
}

M68K_struct M68KMusashi = {
   3,
   "Musashi Interface",
   M68KMusashiInit,
   M68KMusashiDeInit,
   M68KMusashiReset,
   M68KMusashiExec,
   M68KMusashiSync,
   M68KMusashiGetDReg,
   M68KMusashiGetAReg,
   M68KMusashiGetPC,
   M68KMusashiGetSR,
   M68KMusashiGetUSP,
   M68KMusashiGetMSP,
   M68KMusashiSetDReg,
   M68KMusashiSetAReg,
   M68KMusashiSetPC,
   M68KMusashiSetSR,
   M68KMusashiSetUSP,
   M68KMusashiSetMSP,
   M68KMusashiSetFetch,
   M68KMusashiSetIRQ,
   M68KMusashiWriteNotify,
   M68KMusashiSetReadB,
   M68KMusashiSetReadW,
   M68KMusashiSetWriteB,
   M68KMusashiSetWriteW,
   M68KMusashiSaveState,
   M68KMusashiLoadState
};
