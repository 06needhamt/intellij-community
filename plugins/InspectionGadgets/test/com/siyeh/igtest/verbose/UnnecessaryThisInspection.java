package com.siyeh.igtest.verbose;

public class UnnecessaryThisInspection
{
    private int m_foo;

    public void fooBar()
    {
        this.m_foo = 3;
    }

    public void fooBaz( int m_foo)
    {
        this.m_foo = 3;
    }

    public void fooBarangus()
    {
        int m_foo;
        this.m_foo = 3;
    }

    public void fooBarzoom()
    {
        for(int m_foo = 0;m_foo<4; m_foo++)
        {
            this.m_foo = 3;
        }
        this.fooBar();
    }

}
